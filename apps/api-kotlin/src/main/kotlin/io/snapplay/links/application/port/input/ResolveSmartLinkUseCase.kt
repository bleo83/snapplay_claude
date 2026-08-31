package io.snapplay.links.application.port.input

import java.net.URI

fun interface ResolveSmartLinkUseCase {
    /** Returns the redirect URI for the given short code, or null if the link is not resolvable. */
    fun resolve(shortCode: String): URI?
}
