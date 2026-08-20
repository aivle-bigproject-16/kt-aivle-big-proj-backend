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

    @Test
    void aiRetryKeepsCaptureAttemptAndReturnsInspectionToCaptured() {
        Inspection inspection = Inspection.create(
                null,
                null,
                InspectionType.CT
        );
        inspection.startCapture();
        inspection.completeCapture();
        inspection.startAnalysis("first-request-id");

        inspection.prepareAiRetry(
                InspectionFailureType.AI,
                "MODEL_INFERENCE_ERROR"
        );

        assertThat(inspection.getStatus()).isEqualTo(InspectionStatus.CAPTURED);
        assertThat(inspection.getAiRetryCount()).isEqualTo(1);
        assertThat(inspection.getCaptureRetryCount()).isZero();
        assertThat(inspection.currentAttemptNo()).isEqualTo(1);
        assertThat(inspection.getAiRequestId()).isNull();
        assertThat(inspection.canRetryAi(2)).isTrue();
    }
}
