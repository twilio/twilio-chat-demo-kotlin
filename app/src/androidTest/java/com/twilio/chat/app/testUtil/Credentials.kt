package com.twilio.chat.app.testUtil

import com.twilio.chat.app.INVALID_IDENTITY
import com.twilio.chat.app.INVALID_PASSWORD
import com.twilio.chat.app.VALID_IDENTITY
import com.twilio.chat.app.VALID_PASSWORD
import com.twilio.chat.app.data.CredentialStorage

fun setValidCredentials(credentialStorage: CredentialStorage) {
    credentialStorage.storeCredentials(VALID_IDENTITY, VALID_PASSWORD)
}

fun setInvalidCredentials(credentialStorage: CredentialStorage) {
    credentialStorage.storeCredentials(INVALID_IDENTITY, INVALID_PASSWORD)
}
