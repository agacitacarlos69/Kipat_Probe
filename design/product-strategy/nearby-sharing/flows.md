# Nearby sharing: flows

**Concept only. Not built, not scheduled.** The gate is in `docs/product/feature-gates.md`, and the
transport study is in `docs/product/nearby-sharing-technical-options.md`.

## Core rule: identity before content

```
Generic sharing (Quick Share, AirDrop):   device ──► file
Kipat:                                    person ──► identity ──► context ──► file
                                          (tap)      (Reveal)     (Connect,      (one item,
                                                                   met where)     accepted)
```

What identity first plausibly buys, stated without overclaiming:

| Benefit | Why it is plausible | What it does **not** mean |
|---|---|---|
| **Trust** | The receiver sees a name, role and face they just met *before* any content | Not verified identity. HCE can be spoofed, and the Reveal says "Unverified" |
| **Context** | The item lands on a Person ("Ana sent design.pdf after the hackathon") | Not an inbox or a chat |
| **Fewer accidental sends** | You send to the person whose Reveal you are looking at, not a device name in a list | Not impossible to mis-send: two people can tap in a crowd |

## Naming

| Name | For | Against | Verdict |
|---|---|---|---|
| **Kipat Share** (working) | Clear internal name | Collides with Home's primary action **Share** (share *my identity*). Two "Share"s in one app | Internal only |
| **Send to Ana** (verb + person, no brand) | Says exactly what happens, keeps the person first, and doesn't collide | Not a feature name you can market | **Recommended for UI** |
| Abot (Filipino: to hand over or reach) | Local and warm; the act of handing something to someone | Unclear to non-Filipino users; a brand name for a verb | Consider for marketing only, after testing |
| Hand over | Physical, honest | Long; awkward as a button | Reject |
| Pass | Short | Confused with Wallet passes | Reject |
| ~~Kipat AirDrop~~ | — | Another company's trademark, and positions Kipat as a file utility | **Never** |

Copy rule: the UI says **Send** for content and keeps **Share** for sharing your own identity.

## Flows

Shared preconditions: both phones have Kipat, are unlocked, and have Kipat Tap on. The tap has
produced a Reveal on both. Steps marked *(S0)* need no new transport. Everything else is S1+.

### A. Tap → Reveal → Connect (exists today)
1. Touch phones → each sees the other's Reveal over their current app.
2. Ben taps **Connect** → "Connected" (accent tint). The Connection is stored with "Phone tap".
3. Ben dismisses → back to his app. **No send affordance has appeared yet.**

### B. → Send file
1. After A2, Ben taps **Send to Ana** (a text action that appears only after Connect, option B).
2. System document picker (`ACTION_OPEN_DOCUMENT`) → Ben picks **one** file.
3. Ben's surface shows "Hold your phone near Ana's" *(if the session has ended)* or "Sending to Ana…".
4. Ana's phone shows the consent sheet: Ben's identity (compact), "Ben wants to send", file name,
   size, type → **[Decline] [Accept]**.
5. Accept → transfer → hash verified → "Saved to Downloads" with an **Open** text button. Ben sees "Sent".
6. The item is recorded on both Persons (S2).

### C. → Send photo
Same as B, but step 2 uses the **system photo picker** (one image). No `READ_MEDIA_IMAGES`.
The consent sheet shows "photo, 3.1 MB", **no thumbnail before Accept**.

### D. → Share link *(S0)*
1. From the Person screen (not the Reveal), **Share link** → the system Sharesheet with the URL.
2. The user picks any channel. Android's Tap to share and Quick Share are already in that sheet.
3. Kipat records nothing. It is the user's own action in another app.

### E. → Share contact *(S0)*
In a phone tap, both sides **already exchanged public profiles**. Sending your own contact again is
redundant. "Share contact" therefore means **sending a third person's contact** (introducing Ana to
Cara). From Cara's Person → **Share contact** → Sharesheet with a vCard or Kipat link. Not part of
the Reveal. This also guards against the Reveal becoming a menu.

### F. Dismiss after Reveal
1. Reveal → ✕, Back, or backdrop tap → back to the previous app.
2. A Recent item: "Tapped just now, not connected". No send affordance was ever shown, nothing was
   sent, and the other side is not notified.

### G. Receiver rejects
1. Ana taps **Decline** (or the sheet expires: phones separated or timeout).
2. Ben sees "Not accepted". Never "Ana declined", and never a reason.
3. No retry button. To try again, Ben must tap again (a fresh session). One pending offer per peer.

### H. Transfer fails midway
1. The link drops (people move apart, radio failure).
2. The receiver deletes the partial file at once. Nothing partial is ever visible.
3. Both see: "Transfer stopped. Hold your phones together and try again." with a **Try again**
   text button, valid only while the session secret is unexpired. After that, a new tap is needed.
4. No automatic background retry. No foreground service in S1.

### I. Multiple nearby devices
1. The transport never lists nearby devices. The peer is the one whose NFC contact produced the
   session secret.
2. If two people tap Ben in quick succession, each tap is its own Reveal and session. **Send to Ana**
   is bound to Ana's Reveal or Person, never to "the nearest phone".
3. If another device tries to join a session it has no secret for, it is refused silently.

### J. Offline transfer
1. No internet and no shared Wi-Fi: the NFC bootstrap and the local P2P transport work offline by design.
2. With no high-bandwidth path available (radio off, OEM blocks), the item is **not** sent over NFC.
   The user sees "Can't send here. Use Share link instead." and is offered the S0 path.
3. Nothing is queued to send later. An offer exists only while both people are present.
