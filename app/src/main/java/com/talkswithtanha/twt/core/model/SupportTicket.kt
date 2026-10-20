package com.talkswithtanha.twt.core.model

import java.util.Date

/**
 * A support problem with a beginning and an end.
 *
 * The difference between this and the "Talks with Tanha" room is the difference
 * between a conversation and a job. A chat message can be read and let go; a
 * ticket stays in the admin panel's queue, carries a state, and the member is
 * emailed whenever it moves — which is why it exists at all. Somebody whose
 * access code will not work at eleven at night needs the problem recorded, not
 * a message in a thread that scrolls away.
 *
 * Either side can open one. Staff raising a ticket on a member's behalf is the
 * common case after a WhatsApp call: it gives a promise somewhere to live.
 *
 * Mirrors `SupportTicket` in iOS and `Ticket` in twt-admin/lib/tickets-repo.ts.
 */
data class SupportTicket(
    val id: String,
    val subject: String,
    val category: String,
    val status: TicketStatus,
    val priority: TicketPriority,
    val memberId: String,
    val memberName: String,
    val memberEmail: String,
    /** Staff opened it after a call, rather than the member. */
    val openedByStaff: Boolean,
    val createdAt: Date,
    val updatedAt: Date,
    val lastMessage: String,
    val lastMessageAt: Date?,
    val lastMessageFromStaff: Boolean,
    /** The member spoke last — this is what puts it in Tanha's queue. */
    val needsReply: Boolean,
    /** Staff replied and the member has not opened it since. */
    val unreadForMember: Boolean,
    val messageCount: Int
) {
    companion object {
        /**
         * What the app offers when raising one. Kept in step with
         * `TICKET_CATEGORIES` in the admin panel so its filters line up.
         */
        val categories = listOf("Access", "Payment", "Signals", "Account", "App problem", "Other")
    }
}

enum class TicketStatus(val stored: String) {
    OPEN("open"),
    IN_PROGRESS("inProgress"),
    WAITING_ON_MEMBER("waitingOnMember"),
    RESOLVED("resolved"),
    CLOSED("closed");

    /**
     * What the member is told. Deliberately not the stored value: "waiting on
     * member" is admin-panel language, and to the person it is about it means
     * "your turn".
     */
    val memberLabel: String
        get() = when (this) {
            OPEN -> "Open"
            IN_PROGRESS -> "Being looked at"
            WAITING_ON_MEMBER -> "Your turn"
            RESOLVED -> "Resolved"
            CLOSED -> "Closed"
        }

    val isFinished: Boolean get() = this == RESOLVED || this == CLOSED

    companion object {
        fun from(value: String?): TicketStatus =
            entries.firstOrNull { it.stored == value } ?: OPEN
    }
}

enum class TicketPriority(val stored: String) {
    NORMAL("normal"),
    HIGH("high");

    companion object {
        fun from(value: String?): TicketPriority =
            entries.firstOrNull { it.stored == value } ?: NORMAL
    }
}

data class TicketMessage(
    val id: String,
    val text: String,
    val senderId: String,
    val senderName: String,
    /**
     * Drawn as the team's side of the thread. The security rules require a
     * member's own message to carry `false`, so this cannot be forged.
     */
    val isStaff: Boolean,
    val timestamp: Date
)
