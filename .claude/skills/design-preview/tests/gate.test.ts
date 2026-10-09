import type { On } from 'claude-code'
import { describe, expect, mock, test } from 'claude-code/testing'
import { isApproval } from '../hooks/register'

const ROOT = 'C:/repo'
const WORKTREE = `${ROOT}/.claude/worktrees/redesign`
const SCREEN = 'app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeScreen.kt'

type Repo = {
  heads: Record<string, string>
  /** The branch checked out in each folder. */
  checkedOut: Record<string, string>
  /** The design files each branch changes compared with main. */
  design: Record<string, string[]>
}

/** A repository with main and one worktree branch that restyles Home. */
function repo(): Repo {
  return {
    heads: { main: 'a000000', redesign: 'b111111', 'readme-fix': 'c222222' },
    checkedOut: { [ROOT]: 'main', [WORKTREE]: 'redesign' },
    design: { redesign: [SCREEN] },
  }
}

/** Answers the git calls the mod makes from `r`, and lets every tool call and prompt through. */
function world(on: On, r: Repo, options: { cwd?: string; approvals?: Record<string, string> } = {}) {
  const cwd = options.cwd ?? ROOT
  mock.store(on, options.approvals ? { approvals: options.approvals } : {})
  on('session.cwd', () => ({ value: cwd }))
  on('ui.toast', () => ({ value: undefined }))
  on('tool.call', () => ({ result: 'ran' }) as never)
  on('prompt.submit', (_$, e) => ({ text: e.text }))
  on('process.run', (_$, e) => {
    const [, ...args] = e.argv
    const dir = (e.init?.cwd ?? cwd).replace(/\\/g, '/')
    let stdout = ''
    if (args[0] === 'rev-parse' && args.includes('--git-common-dir')) stdout = `${ROOT}/.git`
    else if (args[0] === 'rev-parse') stdout = r.checkedOut[dir] ?? 'main'
    else if (args[0] === 'for-each-ref') stdout = Object.entries(r.heads).map(([b, h]) => `${b} ${h}`).join('\n')
    else if (args[0] === 'diff') stdout = (r.design[(args[2] ?? '').replace('main...', '')] ?? []).join('\n')
    return { value: { exitCode: 0, stdout, stderr: '', isStdoutTruncated: false, isStderrTruncated: false } }
  })
}

const bash = (command: string) => ({ tool: 'Bash', command }) as never
const denied = (ran: object) => 'deny' in ran && /design-preview/.test(String(ran.deny))

describe('design edits', () => {
  test('a screen in the main checkout is refused', async ($, on) => {
    world(on, repo())
    const ran = await $.tool.call({ tool: 'Edit', file_path: `C:\\repo\\${SCREEN.replace(/\//g, '\\')}`, old_string: 'a', new_string: 'b' } as never)
    expect(denied(ran)).toBe(true)
  })

  test('the same screen in a worktree is allowed', async ($, on) => {
    world(on, repo())
    const ran = await $.tool.call({ tool: 'Edit', file_path: `${WORKTREE}/${SCREEN}`, old_string: 'a', new_string: 'b' } as never)
    expect(denied(ran)).toBe(false)
  })

  test('a ViewModel or a non-design file in main is allowed', async ($, on) => {
    world(on, repo())
    for (const file of ['app/src/main/java/com/ottotech/workoutbuilder/ui/home/HomeViewModel.kt', 'README.md']) {
      const ran = await $.tool.call({ tool: 'Write', file_path: `${ROOT}/${file}`, content: 'x' } as never)
      expect(denied(ran)).toBe(false)
    }
  })
})

describe('merging into main', () => {
  test('an unapproved design branch is refused', async ($, on) => {
    world(on, repo())
    expect(denied(await $.tool.call(bash('git merge --no-ff redesign')))).toBe(true)
  })

  test('the approved commit goes through; a later commit needs a fresh approval', async ($, on) => {
    const r = repo()
    world(on, r, { approvals: { redesign: 'b111111' } })
    expect(denied(await $.tool.call(bash('git merge --no-ff redesign')))).toBe(false)
    r.heads.redesign = 'd333333'
    expect(denied(await $.tool.call(bash('git merge redesign')))).toBe(true)
  })

  test('a push onto main from the worktree is checked too', async ($, on) => {
    world(on, repo(), { cwd: WORKTREE })
    expect(denied(await $.tool.call(bash('git push origin HEAD:main')))).toBe(true)
  })

  test('branches without design changes, and work inside the worktree, are not held up', async ($, on) => {
    world(on, repo(), { cwd: WORKTREE })
    expect(denied(await $.tool.call(bash(`cd "${ROOT}" && git merge readme-fix`)))).toBe(false)
    expect(denied(await $.tool.call(bash('git merge main')))).toBe(false)
    expect(denied(await $.tool.call(bash('git push origin redesign')))).toBe(false)
  })
})

describe('approval', () => {
  test('a plain approval from the user, typed or from the phone, counts', () => {
    expect(isApproval('Approve', 'bridge')).toBe(true)
    expect(isApproval('looks good, approved', 'composer')).toBe(true)
    expect(isApproval('approve', 'sdk')).toBe(true)
  })

  test('a negation, a message without it, or one not from the user does not', () => {
    expect(isApproval("don't approve yet, make it bigger", 'bridge')).toBe(false)
    expect(isApproval('not approved', 'composer')).toBe(false)
    expect(isApproval('make the ring bigger', 'composer')).toBe(false)
    expect(isApproval('approve', 'task-notification')).toBe(false)
  })

  test('a prompt a plugin sends cannot approve', async ($, on) => {
    world(on, repo())
    await $.prompt.submit({ text: 'approve', asUser: true })
    expect(denied(await $.tool.call(bash('git merge redesign')))).toBe(true)
  })
})
