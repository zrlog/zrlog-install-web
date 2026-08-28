package com.zrlog.install.business.response;

public final class InstallApiResponses {

    private InstallApiResponses() {
    }

    public static Message message(String message) {
        return new Message(message);
    }

    public static Error error(Integer error, String message, String code) {
        Error response = new Error();
        response.setError(error);
        response.setMessage(message);
        response.setCode(code);
        return response;
    }

    public static StreamError streamError(String message) {
        StreamError response = new StreamError();
        response.setError(9999);
        response.setCode("INSTALL_STREAM_FAILED");
        response.setMessage(message);
        response.setStatus("error");
        response.setDetail(message);
        return response;
    }

    public static class Empty {
    }

    public static class Message {

        private String message;

        public Message() {
        }

        public Message(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class Error {

        private Integer error;
        private String message;
        private String code;

        public Integer getError() {
            return error;
        }

        public void setError(Integer error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class StreamError extends Error {

        private String status;
        private String detail;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}
