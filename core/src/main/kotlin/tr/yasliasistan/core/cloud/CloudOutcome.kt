package tr.yasliasistan.core.cloud

import kotlinx.serialization.json.JsonObject

/** Why no usable answer came from the cloud. Each has its own plain spoken sentence (NFG-32). */
enum class CloudFailure {
    /** No (current) cloud consent: no request was even built (FG-103, NFG-78). */
    NO_CONSENT,

    /** Proxy address / access code not entered: the cloud is off (docs/KURULUM-SON.md §2.10). */
    NOT_CONFIGURED,

    /** Every free model quota is used up (`QUOTA_EXHAUSTED`); no retry, on-device mode (NFG-25). */
    QUOTA_EXHAUSTED,

    /** Proxy abuse guard (`RATE_LIMITED`); never reached by normal use (V-19). */
    RATE_LIMITED,

    /** Wrong or missing access code (`UNAUTHORIZED`). */
    UNAUTHORIZED,

    /** Providers failed or the proxy is misconfigured (`PROVIDERS_UNAVAILABLE`, `UPSTREAM_REJECTED`, 5xx). */
    UNAVAILABLE,

    /** No network or the connection broke. */
    NETWORK,

    /** The proxy did not answer in time. */
    TIMEOUT,

    /** The proxy rejected our request as malformed (400/404/405/413/415): a bug on our side. */
    REQUEST_REJECTED,

    /** The answer did not follow the contract (not JSON, wrong envelope). */
    BAD_RESPONSE,
}

sealed interface CloudOutcome {
    /** The model's JSON object (still masked); G-35's parser checks it strictly. */
    data class Answer(val result: JsonObject) : CloudOutcome {
        override fun toString() = "Answer(fields=${result.size})"
    }

    data class Failed(val failure: CloudFailure) : CloudOutcome
}
