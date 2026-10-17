# Audience prioritization

Qualitative. **No market sizes or survey results are claimed.** Scores are judgements against
Kipat's actual mechanics, which are Android-first, phone-to-phone between Kipat users, a card for
everyone else, and no backend yet. 1 is low, 5 is high.

| Criterion | What it measures |
|---|---|
| Need | How painful in-person introductions are for them today |
| Frequency | How often they meet several new people in a short time |
| Word of mouth | How tightly clustered they are, and how visible a tap is to peers |
| Card | Willingness to carry and pay for a physical card |
| Phone-to-phone | How likely the other person also has Kipat (same cluster, Android) |
| Acquisition | How easy it is to reach many of them at once without ads |

| Audience | Need | Frequency | Word of mouth | Card | Phone-to-phone | Acquisition | Read |
|---|---|---|---|---|---|---|---|
| **Students** (university, orgs) | 4 | 5 (bursts: org week, hackathons, fairs) | **5** (dense clusters, same events, visible) | 3 (price-sensitive, but cards are self-expression) | **5** (same org and cluster; Android-heavy, *assumption, unverified*) | **5** (orgs, hackathons, campus events) | **Primary** |
| **Developers** (students + early career) | 3 | 4 (meetups, hackathons, DevFest) | 5 (they show each other tech) | 3 | 4 | 4 (communities, GDGs) | **Primary (overlaps students)** |
| **Founders** | 5 | 4 (pitch events, demo days) | 3 | 4 | 2 (they meet investors, who are often iPhone users, *unverified*) | 3 | Secondary |
| **Professionals** | 4 | 2–3 (conferences, a few times a year) | 2 | 5 (already carry cards) | 2 (meet mixed, non-Kipat people) | 2 | Secondary |
| **Creators** | 3 | 3 (meetups, collabs) | 4 | 4 (self-expression) | 3 | 3 | Secondary, focused |
| **Organisations** (student orgs, event organisers, small companies) | 3 | 5 (they run the events) | 4 (as channels) | 4 (bulk) | — | **4 as a channel** | **Later, as distribution, bounded** |

## PRIMARY
**Students and student developers** in the Philippines. They meet in bursts, they cluster
(the same people in the same org, class or hackathon, so phone-to-phone works), they show each
other things, and Kipat's no-backend limits hurt least inside one cluster. A card is an
affordable piece of self-expression for them. Their identity changes often (year, org role,
internship), so "edit once, everything updates" fits.

## SECONDARY
**Founders and early-career professionals** at events. They feel the pain strongly, but they meet
mixed audiences (iPhone, no Kipat), so they depend on the card and the web profile, which don't
work yet. **Creators** who want to be met as a person first.

## LATER
**Organisations**, as a distribution partner (orgs, hackathon organisers, career-fair hosts), not
as a buyer of an admin product. They come after the backend exists, and with the boundary below.

---

## Journeys

### Student: from org week to career fair

| Step | What happens | Kipat value |
|---|---|---|
| 1. Org week | Mika's org gives members Kipat cards (or she installs it after a friend tapped her). She sets "BS Computer Science", "Polytechnic University of the Philippines", "Developer, ACM PUP" and picks Precision Orbit | She chooses how she is met |
| 2. Hackathon, Saturday | Teams form fast. She touches phones with three people. Each sees her Reveal over Discord, and she sees theirs. She Connects with two | No spelling handles over noise; mutual |
| 3. Same hackathon | A mentor has no Kipat. She hands over her card → **today**: browser, no profile. **Future**: web profile with Save contact | The card should reach non-users; today it can't |
| 4. Conference, a month later | She meets a speaker. Tap, Connect, and an event label is offered after Connect ("DevFest Manila") | Context of meeting |
| 5. Career fair | A recruiter asks for her details. She taps her card on the recruiter's phone. The recruiter sees her current program and org role | Current identity, no paper résumé card |
| 6. Weeks later | "Who was the backend dev from the hackathon?" → Connections → search "hackathon" → Bea → Replay → right, her → message | **Where they met + current profile** (event search and live profile need the planned work) |

### Professional: why this beats a paper card (without pretending paper is useless)

Paper cards still work. They need no battery, no app or NFC, and they are culturally fine in every
meeting. Kipat should **coexist**, not mock them.

| Moment | Paper card | Kipat |
|---|---|---|
| Handing over | Good ritual | Same ritual (the card), or none (phone) |
| Their side, now | A rectangle in a pocket | A person on their screen, Save contact in one tap |
| Typos and transcription | They type it later, or OCR it | Nothing to type |
| Job change | The card is wrong forever | Edit once (with a backend, for every card already given) |
| Context | Written on the back, if at all | How, when and (next) where, kept |
| Recipient without the app | Works | Needs the web profile (not built) |

Honest claim: **Kipat is better when the other person keeps you. Paper is still fine when they
might not.** Professionals can carry both.

### Creator: person first, not a Linktree wall

Jo is an illustrator at a zine fair. Her Reveal shows **Jo Santos, "Illustrator", "Komiket"** and
her chosen accent. Her portfolio is one link behind **Open profile**, not a wall of buttons on the
first frame. People remember *her*, and the links are one step later. Rule: the Reveal never
becomes a link list, and the profile carries at most the few links the template publishes.

### Organisation: events, booths, recruitment, with a boundary

| Kipat does for an org | Kipat does not do |
|---|---|
| Bulk cards for members or volunteers, each linked by its owner to **their own** identity | Own members' identities or edit them |
| A booth where visitors tap a staffer's card or phone and Connect if they choose | Collect visitor data, scan badges, or capture leads |
| Optional event label so attendees can remember "met at X" | Attendance tracking, check-in, access control |
| A demo table for recruitment week | Admin dashboards, analytics on members, SSO, HR sync |

**Boundary:** an organisation can *distribute* Kipat, but it can never *see through* it. If a
feature needs an admin to see individuals' taps or Connections, it is an enterprise badge system,
and Kipat does not build it.
