# What Connections are for

> **A Connection is a person I intentionally chose to keep after a Kipat interaction.**

A tap creates a *Recent* item. Only **Connect** creates a Connection. This rule is already in the code
(`model/Connection.kt`: "A tap alone never creates a connection") and it is the product.

## Why keep someone in Kipat rather than Contacts

Android Contacts is good at one thing: a phone number you can call. **Save contact** stays one tap
away on every Reveal and Person. Kipat keeps what Contacts does not:

| Reason | What Kipat holds | What Contacts holds | State |
|---|---|---|---|
| **Context of meeting** | How (phone tap, card, QR, link), when, and next *where* (an event name) | A name and number, no history | How and when: built ("Met by"). Where: not built |
| **Profile stays current** | Their identity as they publish it now (role, org, photo) | Whatever was typed that day | Needs the backend resolver. Today it's the snapshot from the tap |
| **Reveal replay** | Their own Reveal, in their chosen style. It helps you remember a face and how you met | A thumbnail | Built (Replay Reveal) |
| **Easier re-contact** | Search by name, role, org, event. Open profile → their public links | Search by name only | Name/role/org search built; event not |
| **Future share history** | What you sent each other through Kipat, attached to the person | Nothing | Not built; conditional (`feature-gates.md`) |

The test for anything added to a Person: **does it help me remember or reach this person?** If not,
it doesn't belong.

## What Connections must never become

| Never | Why |
|---|---|
| A feed, posts, stories, "updates from your connections" | Turns a memory aid into a destination that competes for attention. That is LinkedIn's job |
| Followers, follower counts, mutual-connection counts | Status ranking of people. Kipat records meetings, not popularity |
| Likes, reactions, endorsements | Same |
| Notifications that X updated their profile | Engagement bait. The profile is simply current when *you* open it |
| Leads, pipeline, stages, tags-as-CRM, export to Salesforce | Popl and HiHello territory (`competitive-positioning.md`) |
| Automatic Connections from a tap | Breaks consent and fills the list with noise |
| A directory or "people near you" | Discovery of strangers is not meeting them |
| Connection requests that the other side must approve | A Connection is *my* memory of *you*. It is one-sided by design, like saving a contact. Mutual taps already give both sides the choice |

## Current gaps that matter

1. **Persistence.** Connections and Recent are in memory, and they reset when the process dies.
   That breaks the retention reason at its root. **First fix, before anything else here.**
2. **Where.** "Met by Phone tap, 2 days ago" is good. "At DevFest Manila" is what people actually
   remember. Proposed: an optional event label offered *after* Connect, prefilled from the previous
   Connection made the same day, never from location services.
3. **Current profile.** Requires the backend resolver and a revision per Connection. Until then,
   say nothing. Do not imply freshness.
4. **Remove is final and private.** Removing someone never notifies them. This is already the case
   (local), and it must stay so with a backend.

## Not LinkedIn, explicitly

LinkedIn turns an in-person meeting into a graph edge in a feed product. Kipat turns it into **a
person you can find again, as they are now, with the memory of how you met.** Connections is a
quiet list with search, not a social network.
