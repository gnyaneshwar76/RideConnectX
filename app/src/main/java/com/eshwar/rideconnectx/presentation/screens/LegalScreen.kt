package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.domain.model.LegalContent
import com.eshwar.rideconnectx.domain.model.LegalDoc
import com.eshwar.rideconnectx.domain.model.LegalSection
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * Screens 08 & 09 — Terms & Conditions and Privacy Policy.
 *
 * One screen, two documents. Content comes from [LegalContent], which holds the
 * text supplied by the RideConnectX team.
 *
 * Acceptance is implicit: the Sign In screen states that continuing constitutes
 * agreement, and that is where it is recorded. These screens are for reading.
 */
@Composable
fun LegalScreen(
    doc: LegalDoc,
    onBack: () -> Unit,
) {
    val c = Rcx.colors
    val termsCount = LegalContent.TERMS.size
    val sections = LegalContent.sections(doc)

    ScreenBackdrop {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = doc.screenTitle, onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (doc == LegalDoc.BOTH) {
                    item { DocumentHeading("Terms & Conditions") }
                }

                itemsIndexed(sections) { index, section ->
                    // On the combined page the Privacy Policy needs its own
                    // heading where the Terms end.
                    if (doc == LegalDoc.BOTH && index == termsCount) {
                        Column {
                            DocumentHeading("Privacy Policy")
                            Spacer(Modifier.height(20.dp))
                            LegalSectionBlock(section)
                        }
                    } else {
                        LegalSectionBlock(section)
                    }
                }
            }
        }
    }
}

/** Separates the two documents on the combined page. */
@Composable
private fun DocumentHeading(title: String) {
    val c = Rcx.colors
    Column {
        Text(title, style = RcxType.Wordmark.copy(fontSize = 19.sp), color = c.text)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.border)
        )
    }
}

@Composable
private fun LegalSectionBlock(section: LegalSection) {
    val c = Rcx.colors

    Column {
        Text(
            text = section.title,
            style = RcxType.Label.copy(fontSize = 14.sp),
            color = c.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = section.body,
            style = RcxType.BodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = c.muted,
        )

        section.bullets.forEach { bullet ->
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(c.blue.copy(alpha = 0.7f))
                )
                Text(
                    text = bullet,
                    style = RcxType.BodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                    color = c.muted,
                )
            }
        }

        if (section.tail.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = section.tail,
                style = RcxType.BodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = c.muted,
            )
        }
    }
}
