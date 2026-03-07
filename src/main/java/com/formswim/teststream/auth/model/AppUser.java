package com.formswim.teststream.auth.model;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(
		name = "app_users",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_app_users_email", columnNames = { "email" }) // Unique email
		}
)
public class AppUser {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	/**
	 * Optional team/workspace identifier.
	 *
	 * MVP approach: each user belongs to at most one team.
	 */
	@Column(name = "team_key", length = 100)
	private String teamKey;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role = Role.USER;


	// Protected no-args constructor for JPA
	protected AppUser() { }

	public AppUser(String email, String passwordHash, String teamKey, Role role) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.teamKey = teamKey;
		this.role = role != null ? role : Role.USER;
	}	
	public AppUser(String email, String passwordHash, String teamKey) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.teamKey = teamKey;
	}

	@PrePersist
	@PreUpdate
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		email = normalizeEmail(email);
	}

	public static String normalizeEmail(String email) {
		if (email == null) {
			return null;
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = normalizeEmail(email);
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getTeamKey() {
		return teamKey;
	}

	public void setTeamKey(String teamKey) {
		this.teamKey = teamKey;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}
}
