package com.alyak.userapi.global.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 생성 시각 / 수정 시각을 자동으로 채워 주는 엔티티 공통 부모 클래스.
 *
 * 엔티티가 이 클래스를 상속하면 저장할 때 created_at, 수정할 때 updated_at 이 자동으로 기록된다.
 * (AlyakUserApiApplication 의 @EnableJpaAuditing 이 있어야 동작한다)
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

	/** 처음 저장된 시각 (이후 변경 불가) */
	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** 마지막으로 수정된 시각 */
	@LastModifiedDate
	@Column(nullable = false)
	private LocalDateTime updatedAt;
}
