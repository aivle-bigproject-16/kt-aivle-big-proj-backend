package com.aivle.big_project.domain.inspection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionTest {

    @Test
    void truncatesFailureReasonToDatabaseColumnLengthOnCompletion() {
        Inspection inspection = Inspection.create(
                null,
                null,
                InspectionType.CT
        );
        String longReason = "x".repeat(150);

        inspection.completeAnalysis(
                InspectionStatus.FAILED,
                FinalLabel.FAIL,
                InspectionFailureType.CAPTURE,
                longReason
        );

        assertThat(inspection.getFailureReason())
                .hasSize(100)
                .isEqualTo(longReason.substring(0, 100));
    }

    @Test
    void truncatesFailureReasonToDatabaseColumnLengthOnRecapture() {
        Inspection inspection = Inspection.create(
                null,
                null,
                InspectionType.RGB
        );

        inspection.prepareRecapture(
                InspectionFailureType.CAPTURE,
                "y".repeat(101)
        );

        assertThat(inspection.getFailureReason()).hasSize(100);
    }
}
