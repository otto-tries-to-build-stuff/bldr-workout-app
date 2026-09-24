package com.ottotech.workoutbuilder.ai

/**
 * The only shape the Coach may answer in (spec §9). Structured outputs enforce it, so the
 * mapper never sees prose. Numeric minimums are NOT expressible here — the mapper checks them.
 */
object CoachSchema {
    private fun nullable(type: String) = """{"anyOf":[{"type":"$type"},{"type":"null"}]}"""

    val JSON: String = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["name", "type", "sections"],
      "properties": {
        "name": {"type": "string"},
        "type": {"type": "string", "enum": ["Strength", "HIIT", "Mobility"]},
        "sections": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["name", "rounds", "restBetweenExercisesSec", "restBetweenRoundsSec", "items"],
            "properties": {
              "name": {"type": "string"},
              "rounds": {"type": "integer"},
              "restBetweenExercisesSec": ${nullable("integer")},
              "restBetweenRoundsSec": ${nullable("integer")},
              "items": {
                "type": "array",
                "items": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["exerciseId", "exerciseName", "primaryMuscle", "tags", "mode", "durationSec", "sets", "reps", "note"],
                  "properties": {
                    "exerciseId": ${nullable("string")},
                    "exerciseName": {"type": "string"},
                    "primaryMuscle": {"type": "string", "enum": ["Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Cardio", "Mobility", "Full body"]},
                    "tags": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["facet", "value"],
                        "properties": {
                          "facet": {"type": "string", "enum": ["TYPE", "EQUIPMENT", "MUSCLE", "PATTERN"]},
                          "value": {"type": "string"}
                        }
                      }
                    },
                    "mode": {"type": "string", "enum": ["TIMED", "REPS", "MANUAL"]},
                    "durationSec": ${nullable("integer")},
                    "sets": ${nullable("integer")},
                    "reps": ${nullable("integer")},
                    "note": ${nullable("string")}
                  }
                }
              }
            }
          }
        }
      }
    }
    """.trimIndent()
}
