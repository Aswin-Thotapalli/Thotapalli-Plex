# Recorded Plex fixtures

Per the working rules in CLAUDE.md, a Plex response shape that is unclear gets a real
fixture recorded from the server and the mapper written against it.

The fixtures currently here were **authored from the documented response shapes**, not
recorded from a live server, because the mappers were written before the account had been
signed in. They are faithful to the shapes the endpoints in CLAUDE.md section 5 return,
including the fields this client ignores, and they exercise every branch in the mappers.

Replace each one with a real recording as soon as the corresponding endpoint has been hit
against the real server. The tests should keep passing unchanged; if they do not, the
mapper was wrong and the recording is the authority.

| File | Endpoint |
|---|---|
| `library_sections.json` | `GET /library/sections` |
| `library_movies.json` | `GET /library/sections/{key}/all?type=1&sort=titleSort:asc` |
| `library_shows.json` | `GET /library/sections/{key}/all?type=2&sort=titleSort:asc` |
| `collections.json` | `GET /library/sections/{key}/collections` |
| `movie_metadata.json` | `GET /library/metadata/{ratingKey}?includeMarkers=1&includeChapters=1` |
| `episode_metadata.json` | `GET /library/metadata/{ratingKey}?includeMarkers=1&includeChapters=1` |
| `show_children.json` | `GET /library/metadata/{ratingKey}/children` |
| `continue_watching.json` | `GET /hubs/continueWatching/items` |
| `search.json` | `GET /hubs/search?query={q}&limit=50` |
| `resources.json` | `GET https://plex.tv/api/v2/resources` |
