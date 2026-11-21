package com.studiomogli.ytcontrol

import org.json.JSONObject

object CreatorPromptBuilder {

    fun buildPrompt(action: String, contextJson: String): Pair<String, String> {
        val data = try {
            JSONObject(contextJson)
        } catch (e: Exception) {
            JSONObject()
        }

        val pageTitle = data.optString("title", "Unknown Video / Page")
        val url = data.optString("url", "")
        val snippets = data.optString("snippets", "")
        val comments = data.optString("comments", "")

        return when (action) {
            "CHANNEL_RADAR" -> {
                val title = "Proactive Channel Radar"
                val prompt = buildString {
                    append("You are an elite YouTube growth strategist. Do NOT ask me clarifying questions. ")
                    append("Analyze this live YouTube/Studio page data from my channel or niche and proactively tell me what I am NOT seeing:\n\n")
                    append("Page: ").append(pageTitle).append("\n")
                    append("URL: ").append(url).append("\n")
                    if (snippets.isNotBlank()) {
                        append("Visible Videos & Views:\n").append(snippets.take(3500)).append("\n\n")
                    }
                    append("Deliver EXACTLY these 3 sections:\n")
                    append("1. BLINDSPOT IDEAS (3 unexpected high-CTR video concepts I haven't thought of yet — give exact clickable Title + 3-word Thumbnail text + why people will subscribe).\n")
                    append("2. STOP MAKING THIS (1 pattern or topic that is hurting click-through or retention).\n")
                    append("3. NEXT UPLOAD BET (Pick the #1 highest-upside video to film next and explain the 15-second hook).")
                }
                title to prompt
            }

            "MINE_COMMENTS" -> {
                val title = "Comment Goldmine Scanner"
                val prompt = buildString {
                    append("You are a YouTube audience researcher. Mine these viewer comments to find high-demand video ideas that convert viewers into subscribers.\n\n")
                    append("Video: ").append(pageTitle).append("\n")
                    append("URL: ").append(url).append("\n")
                    if (comments.isNotBlank()) {
                        append("Visible Comments:\n").append(comments.take(3500)).append("\n\n")
                    } else {
                        append("Note: Scroll down to load comments if none were captured, or infer top viewer questions for this topic.\n\n")
                    }
                    append("Deliver:\n")
                    append("1. TOP 3 UNANSWERED QUESTIONS / PAIN POINTS viewers are asking.\n")
                    append("2. 3 READY-TO-FILM FOLLOW-UP VIDEOS (Exact Title + Thumbnail 3-word hook) that directly answer these comments.\n")
                    append("3. 1 PINNED COMMENT CTA I can post right now to boost engagement and subscribers.")
                }
                title to prompt
            }

            "HOOKS_AUDIT" -> {
                val title = "Title/Thumb Audit & 15s Hooks"
                val prompt = buildString {
                    append("You are a YouTube packaging & retention expert. Audit this video and make it impossible to scroll past.\n\n")
                    append("Current Video: ").append(pageTitle).append("\n")
                    append("URL: ").append(url).append("\n")
                    if (snippets.isNotBlank()) {
                        append("Context:\n").append(snippets.take(1500)).append("\n\n")
                    }
                    append("Deliver:\n")
                    append("1. NO-REPEAT PACKAGING AUDIT: Give 5 high-CTR Title + Thumbnail Text pairs where the thumbnail text NEVER repeats the title words.\n")
                    append("2. 3 SPOKEN 15-SECOND HOOKS: Write 3 word-for-word opening intros that confirm the click in the first 3 seconds and stop early drop-off.\n")
                    append("3. RETENTION LOOP: Give 1 curiosity gap to plant at 0:20 so viewers stay to the end and subscribe.")
                }
                title to prompt
            }

            "ADAPT_VIDEO" -> {
                val title = "Adapt Outlier Video"
                val prompt = buildString {
                    append("You are a YouTube outlier strategist. Break down WHY this video performed well and how I can adapt its winning psychology for my own channel without copying it.\n\n")
                    append("Reference Video: ").append(pageTitle).append("\n")
                    append("URL: ").append(url).append("\n")
                    if (snippets.isNotBlank()) {
                        append("Page Context:\n").append(snippets.take(1500)).append("\n\n")
                    }
                    append("Deliver:\n")
                    append("1. WHY IT CLICKED (The exact curiosity gap / emotional trigger).\n")
                    append("2. 3 FRESH ADAPTATION ANGLES (Title + 3-word Thumbnail visual concept) I can film.\n")
                    append("3. OPENING 15-SECOND SCRIPT to hook viewers immediately.")
                }
                title to prompt
            }

            else -> {
                "Creator AI Assistant" to "Analyze this YouTube page ($pageTitle — $url) and give me 3 high-CTR video ideas."
            }
        }
    }
}
