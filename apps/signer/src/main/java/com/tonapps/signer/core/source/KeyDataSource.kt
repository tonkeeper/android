package com.tonapps.signer.core.source

import com.tonapps.signer.core.Database
import org.ton.api.pub.PublicKeyEd25519

class KeyDataSource(private val db: Database) {

    fun clear() {
        db.clearAll()
    }

    fun findIdByPublicKey(publicKey: PublicKeyEd25519): Long? {
        return getEntities().find { it.publicKey == publicKey }?.id
    }

    fun setName(id: Long, name: String) {
        db.setKeyName(id, name)
    }

    fun get(id: Long) = db.getKey(id)

    fun getEntities() = db.getAllKeys()

    fun add(
        name: String,
        publicKey: PublicKeyEd25519
    ): Long {
        return db.insertKey(name, publicKey)
    }

    fun delete(id: Long) {
        db.deleteKey(id)
    }
}