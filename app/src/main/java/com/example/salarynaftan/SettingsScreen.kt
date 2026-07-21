package com.example.salarynaftan

import android.media.RingtoneManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {

    val context = LocalContext.current
    val settings = remember {
        SettingsManager(context)
    }


    var volume by remember {
        mutableFloatStateOf(settings.getVolume())
    }


    var ringtoneName by remember {
        mutableStateOf(settings.getRingtoneName())
    }


    var brigade by remember {
        mutableIntStateOf(settings.getBrigade())
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),

        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {


        Text(
            text = "⚙ НАСТРОЙКИ",
            fontSize = 20.sp,
            color = Color(0xFF00E676)
        )



        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text("Темная тема")

                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = {

                        onThemeChange(it)
                        settings.saveTheme(it)

                    }
                )
            }
        }




        Card(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(12.dp)
            ) {

                Text(
                    "Громкость будильника",
                    color = Color(0xFF00E676)
                )


                Slider(
                    value = volume,

                    onValueChange = {

                        volume = it
                        settings.saveVolume(it)

                    }
                )


                Text(
                    "${(volume * 100).toInt()} %"
                )

            }
        }





        Card(
            modifier = Modifier.fillMaxWidth()
        ) {


            Column(
                modifier = Modifier.padding(12.dp)
            ) {


                Text(
                    "Мелодия будильника",
                    color = Color(0xFF00E676)
                )


                Text(
                    ringtoneName,
                    modifier = Modifier.padding(vertical = 8.dp)
                )


                Button(
                    onClick = {


                        val uri =
                            RingtoneManager
                                .getDefaultUri(
                                    RingtoneManager.TYPE_ALARM
                                )


                        settings.saveRingtoneUri(
                            uri.toString()
                        )


                        ringtoneName =
                            settings.getRingtoneName()

                    }
                ) {

                    Text("Выбрать мелодию")

                }

            }

        }




        Card(
            modifier = Modifier.fillMaxWidth()
        ) {


            Column(
                modifier = Modifier.padding(12.dp)
            ) {


                Text(
                    "Бригада",
                    color = Color(0xFF00E676)
                )


                Row {

                    for(i in 1..4){

                        FilterChip(

                            selected = brigade == i,

                            onClick = {

                                brigade = i
                                settings.setBrigade(i)

                            },

                            label = {
                                Text("$i")
                            }
                        )


                        Spacer(
                            modifier = Modifier.width(5.dp)
                        )

                    }

                }

            }

        }


    }

}