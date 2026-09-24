package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.TrainingType

/**
 * The only shape the Coach may answer in (spec §9). Structured outputs enforce it, so the
 * mapper never sees prose. Numeric minimums are NOT expressible here — the mapper checks them.
 */
object CoachSchema {
    private fun nullable(type: String) = """{"anyOf":[{"type":"$type"},{"type":"null"}]}"""

    /** Enum values come from the domain, so a new muscle, type or mode reaches the Coach too. */
    private fun enumOf(values: List<String>) = values.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }

    val JSON: String = """
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["name", "type", "sections"],
      "properties": {
        "name": {"type": "string"},
        "type": {"type": "string", "enum": ${enumOf(TrainingType.entries.map { it.label })}},
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
                    "primaryMuscle": {"type": "string", "enum": ${enumOf(Muscle.entries.map { it.label })}},
                    "tags": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["facet", "value"],
                        "properties": {
                          "facet": {"type": "string", "enum": ${enumOf(Facet.entries.map { it.name })}},
                          "value": {"type": "string"}
                        }
                      }
                    },
                    "mode": {"type": "string", "enum": ${enumOf(ItemMode.entries.map { it.name })}},
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
