import json
import os
import random
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen


HOST = "localhost"
PORT = 8000
API_KEY = os.getenv("AI_INTERNAL_API_KEY", "local-test-key")
ANALYSIS_DELAY_SECONDS = 2
FAILURE_RATE = 0.05


def utc_now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def send_callback(request_body):
    time.sleep(ANALYSIS_DELAY_SECONDS)

    failed = random.random() < FAILURE_RATE

    if failed:
        capture_failure = random.random() < 0.5
        failure_type = "CAPTURE" if capture_failure else "AI"
        failure_reason = (
            "IMAGE_BLUR"
            if capture_failure
            else "MODEL_INFERENCE_ERROR"
        )

        callback_body = {
            "requestId": request_body["requestId"],
            "batchId": request_body["batchId"],
            "inspectionId": request_body["inspectionId"],
            "batteryCellId": request_body["batteryCellId"],
            "cellSerialNo": request_body["cellSerialNo"],
            "cellStatus": "FAILED",
            "finalLabel": None,
            "failureType": failure_type,
            "failureReason": failure_reason,
            "confidence": None,
            "completedAt": utc_now(),
            "imageResults": []
        }
    else:
        callback_body = build_success_callback(request_body)

    send_callback_request(request_body["callbackUrl"], callback_body)


def build_success_callback(request_body):
    image_results = []

    for image in request_body["images"]:
        image_results.append({
            "imageId": image["imageId"],
            "imageType": image["imageType"],
            "label": "PASS",
            "confidence": 0.99,
            "defects": [],
            "rawResponse": {
                "mock": True,
                "message": "Mock AI analysis completed"
            },
            "latencyMs": 200,
            "errorCode": None,
            "errorMessage": None
        })

    return {
        "requestId": request_body["requestId"],
        "batchId": request_body["batchId"],
        "inspectionId": request_body["inspectionId"],
        "batteryCellId": request_body["batteryCellId"],
        "cellSerialNo": request_body["cellSerialNo"],
        "cellStatus": "COMPLETED",
        "finalLabel": "PASS",
        "failureType": None,
        "failureReason": None,
        "confidence": 0.99,
        "completedAt": utc_now(),
        "imageResults": image_results
    }


def send_callback_request(callback_url, callback_body):
    data = json.dumps(callback_body).encode("utf-8")

    callback_request = Request(
        callback_url,
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Internal-Api-Key": API_KEY
        }
    )

    try:
        with urlopen(callback_request, timeout=10) as response:
            print(
                "[CALLBACK SUCCESS]",
                response.status,
                response.read().decode("utf-8")
            )
    except Exception as error:
        print("[CALLBACK FAILED]", error)


class MockAiHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        if self.path != "/ai/cells/analyze":
            self.send_error(404, "Not Found")
            return

        api_key = self.headers.get("X-Internal-Api-Key")

        if api_key != API_KEY:
            self.send_error(401, "Unauthorized")
            return

        content_length = int(self.headers.get("Content-Length", 0))
        request_body = json.loads(
            self.rfile.read(content_length).decode("utf-8")
        )

        print(
            "[ANALYSIS ACCEPTED]",
            "inspectionId=", request_body["inspectionId"],
            "type=", request_body["images"][0]["imageType"]
        )

        threading.Thread(
            target=send_callback,
            args=(request_body,),
            daemon=True
        ).start()

        response_body = {
            "accepted": True,
            "requestId": request_body["requestId"],
            "inspectionId": request_body["inspectionId"],
            "batteryCellId": request_body["batteryCellId"],
            "status": "ACCEPTED",
            "acceptedAt": utc_now()
        }

        response_bytes = json.dumps(response_body).encode("utf-8")

        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.end_headers()
        self.wfile.write(response_bytes)


if __name__ == "__main__":
    print(f"Mock AI server started: http://{HOST}:{PORT}")
    ThreadingHTTPServer(
        (HOST, PORT),
        MockAiHandler
    ).serve_forever()
