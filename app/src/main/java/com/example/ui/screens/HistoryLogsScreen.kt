package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FeedCommentEntity
import com.example.data.model.FeedPostEntity
import com.example.data.model.FeedScope
import com.example.data.model.HybridFeedItem
import com.example.ui.components.HackerButton
import com.example.ui.components.PublishIntelModal
import com.example.ui.components.TerminalContainer
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.MatrixBorder
import com.example.ui.theme.MatrixBorderBright
import com.example.ui.theme.MatrixDarkBackground
import com.example.ui.theme.MatrixDarkSurface
import com.example.ui.theme.MatrixDarkSurfaceVariant
import com.example.ui.theme.MatrixGreenDim
import com.example.ui.theme.MatrixGreenGlow
import com.example.ui.theme.MatrixGreenPrimary
import com.example.ui.theme.MatrixGreenSecondary
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.theme.MatrixTextPrimary
import com.example.ui.theme.MatrixTextSecondary
import com.example.ui.viewmodel.IntelViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HistoryLogsTab {
    RECENT_IPS,
    CREW_FEED,
    GLOBAL_FEED,
    CONSOLE
}

@Composable
fun HistoryLogsScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var activeTab by remember { mutableStateOf(HistoryLogsTab.RECENT_IPS) }

    val internalTargets by viewModel.internalTargets.collectAsState()
    val externalTargets by viewModel.externalTargets.collectAsState()
    val generalTargets by viewModel.generalTargets.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val crewIdInput by viewModel.crewIdInput.collectAsState()
    val scannerState by viewModel.scannerState.collectAsState()

    val crewHybridFeed by viewModel.crewHybridFeed.collectAsState()
    val globalHybridFeed by viewModel.globalHybridFeed.collectAsState()
    val isPublishModalOpen by viewModel.isPublishModalOpen.collectAsState()

    val activeHandle = currentProfile.trim().ifBlank { "m0lt0rn" }
    val activeCrew = crewIdInput.trim().ifBlank { "CCC" }

    // Extract unique recent IPs with their context
    val recentIpsList = remember(internalTargets, externalTargets, generalTargets) {
        val allTargets = internalTargets + externalTargets + generalTargets
        allTargets.filter { it.ip.isNotBlank() }
            .distinctBy { it.ip }
            .sortedByDescending { it.lastUpdated }
    }

    if (isPublishModalOpen) {
        PublishIntelModal(
            viewModel = viewModel,
            onDismiss = { viewModel.closePublishModal() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "ACTIVITY LOGS & INTERACTIVE INTEL FEEDS",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        // Sub-tabs navigation for Logs / Activity
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(4.dp))
                .background(MatrixDarkSurfaceVariant)
                .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HackerButton(
                text = if (activeTab == HistoryLogsTab.RECENT_IPS) "[RECENT IPs]" else "RECENT IPs",
                onClick = { activeTab = HistoryLogsTab.RECENT_IPS },
                testTag = "tab_recent_ips_btn"
            )
            HackerButton(
                text = if (activeTab == HistoryLogsTab.CREW_FEED) "[CREW FEED]" else "CREW FEED",
                onClick = { activeTab = HistoryLogsTab.CREW_FEED },
                testTag = "tab_crew_feed_btn"
            )
            HackerButton(
                text = if (activeTab == HistoryLogsTab.GLOBAL_FEED) "[GLOBAL FEED]" else "GLOBAL FEED",
                onClick = { activeTab = HistoryLogsTab.GLOBAL_FEED },
                testTag = "tab_global_feed_btn"
            )
            HackerButton(
                text = if (activeTab == HistoryLogsTab.CONSOLE) "[CONSOLE]" else "CONSOLE",
                onClick = { activeTab = HistoryLogsTab.CONSOLE },
                testTag = "tab_console_btn"
            )
        }

        when (activeTab) {
            HistoryLogsTab.RECENT_IPS -> {
                TerminalContainer(
                    title = "ATTACK ROUTE // RECENT SEARCHED IPs",
                    trailingBadge = "${recentIpsList.size} TARGET IPs"
                ) {
                    Text(
                        text = "> Quick-copy IP addresses to streamline your next network strike.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixTextMuted
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (recentIpsList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No recent target IPs recorded in buffer.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(recentIpsList) { target ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "> IP: ${target.ip}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MatrixGreenGlow
                                        )
                                        Text(
                                            text = "Wallet: ${target.wallet.ifBlank { "N/A" }} | Scope: ${target.scope.name}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = MatrixTextSecondary
                                        )
                                    }
                                    HackerButton(
                                        text = "[COPY IP]",
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(target.ip))
                                            Toast.makeText(context, "IP ${target.ip} copied", Toast.LENGTH_SHORT).show()
                                        },
                                        testTag = "copy_ip_${target.ip}"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HistoryLogsTab.CREW_FEED -> {
                // Top Quick Action & Feed Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MatrixGreenGlow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CREW FEED // CLAN [$activeCrew]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenGlow
                            )
                        }
                        Text(
                            text = "Encrypted forum & automated target events",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextMuted
                        )
                    }

                    HackerButton(
                        text = "[+ PUBLISH INTEL]",
                        onClick = { viewModel.openPublishModal(FeedScope.CREW) },
                        testTag = "crew_publish_intel_btn"
                    )
                }

                // Feed List (Interactive Posts + Automated Ingest Events)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (crewHybridFeed.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurface)
                                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No crew intel posts yet. Be the first to publish.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(crewHybridFeed, key = {
                            when (it) {
                                is HybridFeedItem.UserPostItem -> "post_${it.post.id}"
                                is HybridFeedItem.SystemEventItem -> it.id
                            }
                        }) { item ->
                            when (item) {
                                is HybridFeedItem.UserPostItem -> {
                                    FeedPostCard(
                                        post = item.post,
                                        comments = item.comments,
                                        isExpanded = item.isExpanded,
                                        isUpvoted = item.isUpvoted,
                                        activeHandle = activeHandle,
                                        onToggleExpand = { viewModel.togglePostExpansion(item.post.id) },
                                        onUpvote = { viewModel.togglePostUpvote(item.post.id) },
                                        onAddComment = { content ->
                                            viewModel.addCommentToFeedPost(item.post.id, item.post.remoteId, content)
                                        }
                                    )
                                }
                                is HybridFeedItem.SystemEventItem -> {
                                    SystemEventCard(
                                        event = item,
                                        activeHandle = activeHandle
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HistoryLogsTab.GLOBAL_FEED -> {
                // Top Quick Action & Feed Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "GLOBAL INTEL FEED // ALL CREWS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberAmber
                            )
                        }
                        Text(
                            text = "Global hacking community & milestone stream",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextMuted
                        )
                    }

                    HackerButton(
                        text = "[+ GLOBAL POST]",
                        onClick = { viewModel.openPublishModal(FeedScope.GLOBAL) },
                        testTag = "global_publish_intel_btn"
                    )
                }

                // Global Feed List
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (globalHybridFeed.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurface)
                                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Global intel feed active. Awaiting broadcasts...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(globalHybridFeed, key = {
                            when (it) {
                                is HybridFeedItem.UserPostItem -> "glob_post_${it.post.id}"
                                is HybridFeedItem.SystemEventItem -> it.id
                            }
                        }) { item ->
                            when (item) {
                                is HybridFeedItem.UserPostItem -> {
                                    FeedPostCard(
                                        post = item.post,
                                        comments = item.comments,
                                        isExpanded = item.isExpanded,
                                        isUpvoted = item.isUpvoted,
                                        activeHandle = activeHandle,
                                        onToggleExpand = { viewModel.togglePostExpansion(item.post.id) },
                                        onUpvote = { viewModel.togglePostUpvote(item.post.id) },
                                        onAddComment = { content ->
                                            viewModel.addCommentToFeedPost(item.post.id, item.post.remoteId, content)
                                        }
                                    )
                                }
                                is HybridFeedItem.SystemEventItem -> {
                                    SystemEventCard(
                                        event = item,
                                        activeHandle = activeHandle
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HistoryLogsTab.CONSOLE -> {
                TerminalContainer(
                    title = "SYSTEM TERMINAL LOG CONSOLE",
                    trailingBadge = "${scannerState.terminalLogs.size} EVENTS"
                ) {
                    Text(
                        text = "> Real-time system console events, parser status & network sync logs.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixTextMuted
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (scannerState.terminalLogs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Terminal console is clean. Awaiting operations.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(scannerState.terminalLogs) { logLine ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = logLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = when {
                                        logLine.contains("SUCCESS", true) || logLine.contains("COMPLETE", true) -> MatrixGreenGlow
                                        logLine.contains("ERROR", true) || logLine.contains("WARN", true) -> CyberAmber
                                        else -> MatrixGreenPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedPostCard(
    post: FeedPostEntity,
    comments: List<FeedCommentEntity>,
    isExpanded: Boolean,
    isUpvoted: Boolean,
    activeHandle: String,
    onToggleExpand: () -> Unit,
    onUpvote: () -> Unit,
    onAddComment: (String) -> Unit
) {
    val isAuthorMe = post.author.equals(activeHandle, ignoreCase = true)
    var replyText by remember { mutableStateOf("") }
    val timeFormatted = formatTimestamp(post.createdAt)

    val tagColor = when {
        post.tag.contains("INTEL", true) -> MatrixGreenGlow
        post.tag.contains("PLAN", true) -> CyberAmber
        post.tag.contains("DISCUS", true) || post.tag.contains("DISCUSS", true) -> MatrixGreenSecondary
        post.tag.contains("ANUNCIO", true) || post.tag.contains("ANNOUNCE", true) -> Color(0xFFFF3366)
        else -> MatrixGreenPrimary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MatrixDarkSurface)
            .border(
                BorderStroke(1.dp, if (isAuthorMe) MatrixGreenGlow.copy(alpha = 0.8f) else MatrixBorderBright),
                RoundedCornerShape(6.dp)
            )
            .padding(10.dp)
    ) {
        // Header: Tag + Author + Timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(tagColor.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, tagColor), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = post.tag,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = tagColor
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "[${post.author}] ${if (isAuthorMe) "(YOU)" else ""}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAuthorMe) MatrixGreenGlow else MatrixTextPrimary
                )
            }

            Text(
                text = timeFormatted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MatrixTextMuted
            )
        }

        // Title (if present)
        if (post.title.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.title,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixGreenPrimary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Body Content
        Text(
            text = post.content,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixTextSecondary,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Footer Actions: Upvote & Comments Thread Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Upvote Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isUpvoted) MatrixGreenGlow.copy(alpha = 0.15f) else MatrixDarkBackground)
                        .border(
                            BorderStroke(1.dp, if (isUpvoted) MatrixGreenGlow else MatrixBorder),
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { onUpvote() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "Upvote",
                        tint = if (isUpvoted) MatrixGreenGlow else MatrixTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${post.upvotes + if (isUpvoted) 1 else 0}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isUpvoted) MatrixGreenGlow else MatrixTextMuted
                    )
                }

                // Comments Toggle Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(MatrixDarkBackground)
                        .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(3.dp))
                        .clickable { onToggleExpand() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MatrixGreenSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${comments.size} Comments",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixGreenSecondary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MatrixGreenSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "SCOPE: ${post.scope}",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = MatrixTextMuted
            )
        }

        // Expandable Thread Comments Section
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF020703))
                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "DISPATCH THREAD // ${comments.size} REPLIES",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenDim
                )

                if (comments.isEmpty()) {
                    Text(
                        text = "No replies yet in this thread.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixTextMuted
                    )
                } else {
                    for (c in comments) {
                        val isCommentMe = c.author.equals(activeHandle, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(MatrixDarkSurfaceVariant)
                                .padding(6.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "> [${c.author}] ${if (isCommentMe) "(YOU)" else ""}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCommentMe) MatrixGreenGlow else MatrixTextPrimary
                                    )
                                    Text(
                                        text = formatTimestamp(c.createdAt),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = MatrixTextMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = c.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MatrixTextSecondary
                                )
                            }
                        }
                    }
                }

                // Quick Comment Input Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Write intel reply...", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MatrixGreenGlow,
                            unfocusedBorderColor = MatrixBorder,
                            focusedTextColor = MatrixGreenPrimary,
                            unfocusedTextColor = MatrixTextPrimary
                        ),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onAddComment(replyText)
                                replyText = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MatrixGreenGlow.copy(alpha = 0.2f))
                            .border(BorderStroke(1.dp, MatrixGreenGlow), RoundedCornerShape(4.dp))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send reply",
                            tint = MatrixGreenGlow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemEventCard(
    event: HybridFeedItem.SystemEventItem,
    activeHandle: String
) {
    val isMe = event.contributor.equals(activeHandle, ignoreCase = true)
    val timeFormatted = formatTimestamp(event.eventTime)

    val (badgeText, badgeColor, icon) = when (event.eventType) {
        "WALLET_MATCH" -> Triple("⚡ WALLET MATCH", CyberAmber, Icons.AutoMirrored.Filled.TrendingUp)
        "RANK_MILESTONE" -> Triple("🏆 RANK MILESTONE", CyberAmber, Icons.Default.EmojiEvents)
        "GLOBAL_RAID" -> Triple("💥 NETWORK BREACH", Color(0xFFFF3366), Icons.AutoMirrored.Filled.TrendingUp)
        else -> Triple("📡 TARGET INDEXED", MatrixGreenPrimary, Icons.Default.EditNote)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MatrixDarkSurfaceVariant)
            .border(
                BorderStroke(1.dp, if (isMe) MatrixGreenGlow else MatrixBorder),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .border(BorderStroke(1.dp, badgeColor), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "OPERATIVE [${event.contributor}] ${if (isMe) "(YOU)" else ""}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) MatrixGreenGlow else MatrixTextPrimary
                    )
                }

                Text(
                    text = timeFormatted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = MatrixTextMuted
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = event.details,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = if (event.eventType == "WALLET_MATCH" || event.eventType == "GLOBAL_RAID") MatrixGreenGlow else MatrixTextSecondary
            )

            if (event.wallet.isNotBlank()) {
                Text(
                    text = "Matched Crypto Wallet: ${event.wallet}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = CyberAmber
                )
            }
        }
    }
}

private fun formatTimestamp(timeMillis: Long): String {
    if (timeMillis <= 0) return "Just now"
    val diff = System.currentTimeMillis() - timeMillis
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timeMillis))
        }
    }
}
