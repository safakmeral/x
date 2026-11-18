package tr.yasliasistan.core.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretFieldTest {

    @Test
    fun passwordVariationsAreSecret() {
        assertTrue(SecretField.isSecret(isPassword = true, inputType = 0, dataSensitive = false))
        assertTrue(SecretField.isPasswordInputType(0x81)) // text password
        assertTrue(SecretField.isPasswordInputType(0x91)) // visible password
        assertTrue(SecretField.isPasswordInputType(0xE1)) // web password
        assertTrue(SecretField.isPasswordInputType(0x12)) // numeric PIN
        assertTrue(SecretField.isSecret(isPassword = false, inputType = 1, dataSensitive = true))
    }

    @Test
    fun ordinaryFieldsAreNotSecret() {
        assertFalse(SecretField.isPasswordInputType(0)) // no input type
        assertFalse(SecretField.isPasswordInputType(0x01)) // plain text
        assertFalse(SecretField.isPasswordInputType(0x21)) // e-mail
        assertFalse(SecretField.isPasswordInputType(0x03)) // phone
        assertFalse(SecretField.isPasswordInputType(0x02)) // number
    }
}
