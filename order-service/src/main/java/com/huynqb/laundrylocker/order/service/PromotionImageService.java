package com.huynqb.laundrylocker.order.service;

import com.huynqb.laundrylocker.common.exception.NotFoundException;
import com.huynqb.laundrylocker.common.media.CloudinaryMediaStorage;
import com.huynqb.laundrylocker.common.media.MediaPurpose;
import com.huynqb.laundrylocker.common.media.MediaUpload;
import com.huynqb.laundrylocker.common.media.VerifiedMedia;
import com.huynqb.laundrylocker.order.model.Promotion;
import com.huynqb.laundrylocker.order.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Ảnh banner khuyến mãi lưu trên Cloudinary (ADR-0004).
@Service
@RequiredArgsConstructor
public class PromotionImageService {

    private final PromotionRepository promotionRepository;
    private final CloudinaryMediaStorage mediaStorage;

    @Transactional
    public Promotion updateImage(Long promotionId, MediaUpload upload, Long actorUserId) {
        Promotion promotion = find(promotionId);
        VerifiedMedia media = mediaStorage.verify(upload, MediaPurpose.PROMOTION_IMAGE, actorUserId);
        String previous = promotion.getImageUrl();
        promotion.setImageUrl(media.secureUrl());
        Promotion saved = promotionRepository.save(promotion);
        mediaStorage.deleteReplacedAfterCommit(previous, media.secureUrl());
        return saved;
    }

    @Transactional
    public Promotion removeImage(Long promotionId) {
        Promotion promotion = find(promotionId);
        String previous = promotion.getImageUrl();
        promotion.setImageUrl(null);
        Promotion saved = promotionRepository.save(promotion);
        mediaStorage.deleteReplacedAfterCommit(previous, null);
        return saved;
    }

    private Promotion find(Long id) {
        return promotionRepository.findById(id).orElseThrow(() -> new NotFoundException("Promotion", id));
    }
}
