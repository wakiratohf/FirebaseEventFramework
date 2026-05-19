package com.example.firebaseeventframework

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.firebaseeventframework.event.AnalyticsEventsUtils
import com.example.firebaseeventframework.event.ButtonName
import com.example.firebaseeventframework.event.ScreenName
import com.example.firebaseeventframework.ui.base.BaseTrackedActivity
import com.example.firebaseeventframework.ui.theme.FirebaseEventFrameworkTheme

class MainActivity : BaseTrackedActivity() {

    override fun screenName(): String = ScreenName.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirebaseEventFrameworkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeContent(
                        modifier = Modifier.padding(innerPadding),
                        onRefreshClick = {
                            AnalyticsEventsUtils.logClickBtn(
                                screenName = ScreenName.HOME,
                                buttonName = ButtonName.REFRESH
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeContent(modifier: Modifier = Modifier, onRefreshClick: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Hello Android!")
        Button(onClick = onRefreshClick) {
            Text(text = "Refresh")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    FirebaseEventFrameworkTheme {
        HomeContent()
    }
}
