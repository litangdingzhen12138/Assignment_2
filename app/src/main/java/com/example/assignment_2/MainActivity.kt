package com.example.assignment_2

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.assignment_2.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null
    private var transcribeJob: Job? = null

    private val pickVideo =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                playUri(uri)
                startModeration(VideoSource.FromUri(uri), currentLang())
            }
        }

    private fun currentLang(): Lang {
        val pos = binding.langSpinner.selectedItemPosition
        return if (pos == 1) Lang.EN else Lang.ZH
    }

    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // Spinner
//        val langs = listOf("中文(离线)", "English(offline)")
//        binding.langSpinner.adapter =
//            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 先设置语言下拉列表
        val langs = listOf("中文(离线)", "English(offline)")
        binding.langSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs)
        binding.langSpinner.setSelection(1)

        binding.btnPickVideo.setOnClickListener {
            pickVideo.launch(arrayOf("video/*"))
        }

        // 默认播放 assets 视频
//        val assetPath = "videos/sample.mp4"
//        playUri(Uri.parse("asset:///$assetPath"))
//        startModeration(VideoSource.FromAsset(assetPath), currentLang())

        val videoList = (assets.list("videos") ?: emptyArray())
            .filter { it.endsWith(".mp4", true) || it.endsWith(".mkv", true) || it.endsWith(".mov", true) }
            .sorted()

        binding.videoSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, videoList)

        if (videoList.isNotEmpty()) {
            // ✅ 让 spinner 默认选第一个（会触发一次 onItemSelected）
            binding.videoSpinner.setSelection(0)
        }

// 选择不同视频时切换播放+识别
        binding.videoSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val selected = "videos/${videoList[position]}"
                    playUri(Uri.parse("asset:///$selected"))
                    startModeration(VideoSource.FromAsset(selected), currentLang())
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }
    }

    private fun playUri(uri: Uri) {
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun startModeration(source: VideoSource, lang: Lang) {
        transcribeJob?.cancel()

        binding.tvStatus.text = "状态：初始化中…"
        binding.tvTranscript.text = "转写：\n"
        binding.tvCounts.text = "脏话次数：0"
        binding.tvEmotion.text = "情绪：-"

        transcribeJob = lifecycleScope.launch {
            val profanity = ProfanityDetector.fromAssets(
                context = this@MainActivity,
                assetPath = if (lang == Lang.ZH) "profanity/zh.txt" else "profanity/en.txt"
            )

            val emotion = EmotionAnalyzer()

//            val engine = VideoAudioModerationEngine(
//                context = this@MainActivity,
//                lang = lang,
//                profanityDetector = profanity,
//                emotionAnalyzer = emotion,
//                onUi = { ui ->
//                    binding.tvStatus.text = "状态：${ui.status}"
//                    binding.tvEmotion.text = "情绪：${ui.emotionLabel}"
//                    binding.tvCounts.text = "脏话次数：${ui.totalProfanityCount}\n${ui.detailCounts}"
//                    binding.tvTranscript.text = "转写：\n${ui.transcript}"
//                }
//            )
            val engine = VideoAudioModerationEngine(
                context = this@MainActivity,
                lang = lang,
                profanityDetector = profanity,
                emotionAnalyzer = emotion,
                onUi = { ui ->
                    runOnUiThread {
                        binding.tvStatus.text = "状态：${ui.status}"
                        binding.tvEmotion.text = "情绪：${ui.emotionLabel}"
                        binding.tvCounts.text = "脏话次数：${ui.totalProfanityCount}\n${ui.detailCounts}"
                        binding.tvTranscript.text = "转写：\n${ui.transcript}"
                    }
                }
            )


            engine.run(source)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transcribeJob?.cancel()
        player?.release()
        player = null
    }
}
