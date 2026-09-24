package com.ottotech.workoutbuilder.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.ottotech.workoutbuilder.domain.Muscle
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachSchemaTest {
    @Test
    fun `the schema is valid JSON and offers every muscle the app knows`() {
        val root = ObjectMapper().readTree(CoachSchema.JSON)
        val muscles = root.at("/properties/sections/items/properties/items/items/properties/primaryMuscle/enum")
            .map { it.asText() }
        assertEquals(Muscle.entries.map { it.label }, muscles)
    }
}
