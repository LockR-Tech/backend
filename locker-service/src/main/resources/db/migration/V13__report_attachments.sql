-- Ảnh đính kèm phiếu sự cố, lưu trên Cloudinary (chỉ giữ metadata ở đây).
-- stage: REPORT     = người báo chụp tại hiện trường khi gửi phiếu
--        INSPECTION = KTV tới nơi chụp xác nhận hiện trạng
--        PROGRESS   = ảnh trong quá trình sửa (gắn với 1 dòng repair_logs)
--        RESOLUTION = ảnh nghiệm thu sau khi sửa xong
-- Bảng mới, không đổi bảng cũ ⇒ tương thích ngược với bản đang chạy.

CREATE TABLE locker_schema.report_attachments
(
    id                  BIGSERIAL PRIMARY KEY,
    report_id           BIGINT        NOT NULL,
    repair_log_id       BIGINT,
    stage               VARCHAR(20)   NOT NULL,
    public_id           VARCHAR(255)  NOT NULL,
    secure_url          VARCHAR(1000) NOT NULL,
    format              VARCHAR(20),
    bytes               BIGINT,
    width               INT,
    height              INT,
    caption             VARCHAR(500),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    captured_at         TIMESTAMP,
    uploaded_by_user_id BIGINT        NOT NULL,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (report_id) REFERENCES locker_schema.locker_reports (id) ON DELETE CASCADE,
    FOREIGN KEY (repair_log_id) REFERENCES locker_schema.repair_logs (id) ON DELETE SET NULL,
    CONSTRAINT uq_report_attachments_public_id UNIQUE (public_id),
    CONSTRAINT ck_report_attachments_stage CHECK (stage IN ('REPORT', 'INSPECTION', 'PROGRESS', 'RESOLUTION'))
);

CREATE INDEX idx_report_attachments_report ON locker_schema.report_attachments (report_id, stage);
CREATE INDEX idx_report_attachments_log ON locker_schema.report_attachments (repair_log_id);
