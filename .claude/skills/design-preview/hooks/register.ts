import type { EngineInterface, Register } from 'claude-code'

// The design gate. Screen changes are drafted in a worktree, shown to the user as before/after
// screenshots (skill: design-preview), and reach main only once the user has replied "approve".
// Two rules hold that order: no design edits in the main checkout, and no merge or push that
// would carry a branch's unapproved design changes into main.

// What counts as design: the screens, plus the app's graphics, fonts and colours. ViewModels
// sit under ui/ but draw nothing, so they are left out.
const UI = 'app/src/main/java/com/ottotech/workoutbuilder/ui/'
const RES = 'app/src/main/res/'
const DESIGN_PATHSPEC = [UI, RES, ':(exclude)*ViewModel.kt']
const isDesignFile = (rel: string) => (rel.startsWith(UI) && !rel.endsWith('ViewModel.kt')) || rel.startsWith(RES)

const MAIN = 'main'
const APPROVALS = 'approvals' // $.store: { [branch]: the commit the user approved }

// Only the user's own message approves: typed here, or sent from the phone or the IDE.
const USER_ORIGINS = new Set(['composer', 'bridge', 'sdk'])
const APPROVES = /\bapproved?\b/i
const WITHHOLDS = /(\bnot|n't|\bnever)\s+(yet\s+)?approved?\b/i

const SKILL_HINT =
  'Draft design changes in a worktree, show the user the before/after preview (skill: design-preview:design-preview), ' +
  'and merge only after they reply "approve".'

type Approvals = Record<string, string>

const norm = (path: string) => path.replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()

async function git($: EngineInterface, args: string[], cwd?: string) {
  const ran = await $.process.run(['git', ...args], cwd === undefined ? {} : { cwd })
  return { ok: ran.exitCode === 0, out: ran.stdout.trim() }
}

/** The main checkout's folder: the parent of the repository's shared .git folder. */
async function mainRoot($: EngineInterface): Promise<string | undefined> {
  const { ok, out } = await git($, ['rev-parse', '--path-format=absolute', '--git-common-dir'])
  return ok ? norm(out).replace(/\/\.git$/, '') : undefined
}

/** The folder a shell command runs git in: its `cd` or `git -C`, else the session's. */
function commandDir(command: string, cwd: string): string {
  const named = /(?:^|&&|;|\|\|)\s*(?:cd|Set-Location)\s+(?:"([^"]+)"|'([^']+)'|(\S+))|\bgit\s+-C\s+(?:"([^"]+)"|'([^']+)'|(\S+))/.exec(command)
  // Git Bash spells C:\ as /c/; the process runner needs the Windows form.
  const dir = named?.slice(1).find(part => part !== undefined)?.replace(/^\/([a-z])\//i, '$1:/')
  if (dir === undefined) return cwd
  return /^([a-z]:|\/)/i.test(dir) ? dir : `${cwd}/${dir}`
}

/** The design files a branch would bring into main. */
async function designChanges($: EngineInterface, branch: string, cwd: string): Promise<string[]> {
  const { ok, out } = await git($, ['diff', '--name-only', `${MAIN}...${branch}`, '--', ...DESIGN_PATHSPEC], cwd)
  return ok && out !== '' ? out.split('\n') : []
}

async function localBranches($: EngineInterface, cwd: string): Promise<Map<string, string>> {
  const { out } = await git($, ['for-each-ref', '--format=%(refname:short) %(objectname)', 'refs/heads'], cwd)
  return new Map(out.split('\n').filter(Boolean).map(line => line.split(' ') as [string, string]))
}

/** The branches a command would carry into main, or none when it leaves main alone. */
async function incomingBranches($: EngineInterface, command: string, cwd: string): Promise<string[]> {
  const branches = await localBranches($, cwd)
  const named = [...branches.keys()].filter(b => b !== MAIN && new RegExp(`(^|[\\s:/'"])${b.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}($|[\\s:'"])`).test(command))
  const { out: current } = await git($, ['rev-parse', '--abbrev-ref', 'HEAD'], cwd)

  // Merging, cherry-picking or rebasing while main is checked out pulls in the named branches.
  if (current === MAIN && /\bgit\b.*\b(merge|cherry-pick|rebase)\b/.test(command)) return named

  // A push onto main from another branch: `git push origin feature:main` or `HEAD:main`.
  const push = /\bgit\b.*\bpush\b.*\s([^\s:]+):(?:refs\/heads\/)?main\b/.exec(command)
  if (push !== null) return [push[1] === 'HEAD' ? current : push[1]].filter(b => b !== MAIN)

  // Merging a pull request on GitHub: its head branch.
  const pr = /\bgh\s+pr\s+merge\b\s*([^\s-]\S*)?/.exec(command)
  if (pr !== null) {
    const view = await $.process.run(['gh', 'pr', 'view', ...(pr[1] ? [pr[1]] : []), '--json', 'headRefName', '-q', '.headRefName'], { cwd })
    return view.exitCode === 0 ? [view.stdout.trim()] : [current]
  }
  return []
}

export const register: Register = on => {
  // Rule 1: no design edits in the main checkout. Worktrees (under .claude/worktrees) are fine.
  for (const tool of ['Edit', 'Write'] as const) {
    on('tool.call', { tool }, async ($, e, next) => {
      const root = await mainRoot($)
      const path = norm(e.file_path)
      if (root === undefined || !path.startsWith(`${root}/`) || path.startsWith(`${root}/.claude/`)) return next(e)
      const rel = e.file_path.replace(/\\/g, '/').slice(root.length + 1)
      if (!isDesignFile(rel)) return next(e)
      return { deny: `design-preview: ${rel} is a design file in the main checkout. ${SKILL_HINT}` }
    })
  }

  // Rule 2: nothing carries unapproved design changes into main.
  for (const tool of ['Bash', 'PowerShell'] as const) {
    on('tool.call', { tool }, async ($, e, next) => {
      const command = String((e as { command?: unknown }).command ?? '')
      if (!/\bgit\b.*\b(merge|cherry-pick|rebase|push)\b|\bgh\s+pr\s+merge\b/.test(command)) return next(e)

      const cwd = commandDir(command, await $.session.cwd())
      const approvals = ((await $.store.get(APPROVALS)) ?? {}) as Approvals
      const heads = await localBranches($, cwd)

      for (const branch of await incomingBranches($, command, cwd)) {
        const files = await designChanges($, branch, cwd)
        if (files.length === 0 || approvals[branch] === heads.get(branch)) continue
        const changed = approvals[branch] === undefined ? 'has not been approved' : 'changed after it was approved'
        return {
          deny: `design-preview: ${branch} changes ${files.length} design file(s) (${files.slice(0, 5).join(', ')}) ` +
            `and ${changed}. ${SKILL_HINT}`,
        }
      }
      return next(e)
    })
  }

  // The user's "approve" signs off every branch that currently differs from main in design, at
  // its current commit; a later commit on that branch needs a fresh approval.
  on('prompt.submit', async ($, e, next) => {
    if (!isApproval(e.text, e.origin?.kind ?? '')) return next(e)

    const cwd = await $.session.cwd()
    const approvals = ((await $.store.get(APPROVALS)) ?? {}) as Approvals
    const signed: string[] = []
    for (const [branch, head] of await localBranches($, cwd)) {
      if (branch === MAIN || approvals[branch] === head) continue
      if ((await designChanges($, branch, cwd)).length === 0) continue
      approvals[branch] = head
      signed.push(`${branch} at ${head.slice(0, 7)}`)
    }
    if (signed.length === 0) return next(e)

    await $.store.set(APPROVALS, approvals)
    $.ui.toast(`Design approved: ${signed.join(', ')}`)
    const note = `design-preview: the user's message approved the design changes on ${signed.join(', ')}. ` +
      'Those commits may now be merged into main; any further commit on the branch needs a new approval.'
    return next({ ...e, context: [...(e.context ?? []), note] })
  })
}

/** A message from the user (typed, phone or IDE) that says "approve" and doesn't negate it. */
export function isApproval(text: string, origin: string): boolean {
  return USER_ORIGINS.has(origin) && APPROVES.test(text) && !WITHHOLDS.test(text)
}
