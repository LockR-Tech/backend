-- Xoá tài liệu ở trang admin là xoá mềm kèm lý do (ADR-0009): bản ghi và nội dung gốc còn trong DB,
-- các đoạn đã nhúng bị xoá nên trợ lý không còn dùng tài liệu đó để trả lời.
ALTER TABLE kb_documents
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deleted_by BIGINT,
    ADD COLUMN deleted_reason VARCHAR(500);

-- Nạp lại đúng tệp đã xoá được phép: chỉ tài liệu chưa xoá mới bị chặn trùng nội dung.
DROP INDEX uq_kb_documents_checksum;
CREATE UNIQUE INDEX uq_kb_documents_checksum ON kb_documents (checksum) WHERE deleted_at IS NULL;
