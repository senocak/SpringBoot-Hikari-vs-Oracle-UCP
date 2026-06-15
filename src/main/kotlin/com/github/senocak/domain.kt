package com.github.senocak

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import org.springframework.data.repository.CrudRepository

@Entity
@Table(name = "users")
class User(
    @Column(name = "name", nullable = false, length = 50) var name: String? = null,
) {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USER_GENERATOR")
    @SequenceGenerator(name = "SEQ_USER_GENERATOR", sequenceName = "users_seq", allocationSize = 1)
    var id: Long? = null
}

interface UserRepository: CrudRepository<User, Long>