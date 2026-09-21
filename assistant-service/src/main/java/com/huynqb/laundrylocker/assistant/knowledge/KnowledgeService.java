package com.huynqb.laundrylocker.assistant.knowledge;

import com.huynqb.laundrylocker.common.exception.BusinessException;
import com.huynqb.laundrylocker.common.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/// Admin quản lý kho tri thức. Nạp xong tài liệu ở trạng thái PENDING; [KnowledgeIndexer] đánh chỉ mục.
@Service
public class KnowledgeService {

    /// Khớp giới hạn body 20 MB của Nginx.
    static final long MAX_BYTES = 20L * 1024 * 1024;

    private final DocumentRepository documents;

    public KnowledgeService(DocumentRepository documents) {
        this.documents = documents;
    }

    public KnowledgeDocument upload(
            String fileName, String mimeType, byte[] content, String title, List<String> allowedRoles, Long actorUserId) {
        if (content == null || content.length == 0) {
            throw new BusinessException("DOCUMENT_EMPTY", "File rỗng");
        }
        if (content.length > MAX_BYTES) {
            throw new BusinessException("DOCUMENT_TOO_LARGE", "File tối đa 20 MB");
        }
        DocumentParser.Format format = DocumentParser.detect(fileName, mimeType);
        if (format == null) {
            throw new BusinessException(
                    "DOCUMENT_TYPE_UNSUPPORTED", "Chỉ hỗ trợ Markdown, TXT, HTML, PDF và DOCX");
        }
        String checksum = sha256(content);
        if (documents.existsByChecksum(checksum)) {
            throw new BusinessException(
                    "DOCUMENT_DUPLICATE", "Tài liệu này đã có trong kho tri thức", HttpStatus.CONFLICT);
        }
        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : defaultTitle(fileName);
        if (resolvedTitle.length() > 255) {
            resolvedTitle = resolvedTitle.substring(0, 255);
        }
        long id = documents.insert(
                resolvedTitle, fileName, mimeFor(format), RoleSet.normalizeAllowed(allowedRoles),
                checksum, content, actorUserId);
        return get(id);
    }

    public List<KnowledgeDocument> list() {
        return documents.findAll();
    }

    public KnowledgeDocument get(long id) {
        return documents.findById(id).orElseThrow(() -> new NotFoundException("KnowledgeDocument", id));
    }

    /// Đánh chỉ mục lại (sau khi đổi model nhúng, hoặc tài liệu FAILED vì lỗi tạm thời).
    public KnowledgeDocument reindex(long id) {
        if (!documents.requeue(id)) {
            throw new NotFoundException("KnowledgeDocument", id);
        }
        return get(id);
    }

    public KnowledgeDocument update(long id, String title, List<String> allowedRoles) {
        get(id);
        if (allowedRoles != null) {
            documents.updateRoles(id, RoleSet.normalizeAllowed(allowedRoles));
        }
        if (StringUtils.hasText(title)) {
            documents.updateTitle(id, title.trim().length() > 255 ? title.trim().substring(0, 255) : title.trim());
        }
        return get(id);
    }

    public void delete(long id) {
        if (!documents.delete(id)) {
            throw new NotFoundException("KnowledgeDocument", id);
        }
    }

    private static String defaultTitle(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "Tài liệu";
        }
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String mimeFor(DocumentParser.Format format) {
        return switch (format) {
            case MARKDOWN -> "text/markdown";
            case TEXT -> "text/plain";
            case HTML -> "text/html";
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        };
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
