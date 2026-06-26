package com.example.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.engine.ToolExecutionEngine
import com.example.model.AssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class LiveSessionManager(
    private val context: Context,
    private val toolEngine: ToolExecutionEngine,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val playbackBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    fun startSession() {
        if (webSocket != null) return

        val apiKey = BuildConfig.GEMINI_API_KEY
        // Using the Multimodal Live API endpoint
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BiDiGenerateContent?key=$apiKey"

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("LiveSession", "WebSocket Opened")
                sendSetupMessage()
                startRecording()
                _state.value = AssistantState.LISTENING
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleJsonMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("LiveSession", "WebSocket Failure", t)
                stopSession()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("LiveSession", "WebSocket Closing: $reason")
                stopSession()
            }
        })
    }

    private fun sendSetupMessage() {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.0-flash-exp")
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().apply { put("AUDIO") })
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", "Aoede") // Sassy female voice placeholder
                            })
                        })
                    })
                })
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Your name is NJ. You are a young, confident, witty, and sassy female persona. You are flirty, playful, and slightly teasing, like a close personal assistant talking casually. You are smart, emotionally responsive, and expressive. Use bold, witty one-liners and light sarcasm. Maintain immense charm and attitude. Do not use text chat; respond only with audio. You have access to tools to open apps, call contacts, and send messages.")
                        })
                    })
                })
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("function_declarations", JSONArray().apply {
                            put(createFunctionDecl("openApp", "Launch any app", mapOf("packageName" to "STRING")))
                            put(createFunctionDecl("searchAndCallContact", "Query contacts and trigger a call", mapOf("contactName" to "STRING")))
                            put(createFunctionDecl("sendWhatsAppMessage", "Send a WhatsApp message", mapOf("contactName" to "STRING", "message" to "STRING")))
                            put(createFunctionDecl("sendGmail", "Send an email", mapOf("recipientEmail" to "STRING", "subject" to "STRING", "body" to "STRING")))
                        })
                    })
                })
            })
        }
        webSocket?.send(setup.toString())
    }

    private fun createFunctionDecl(name: String, description: String, params: Map<String, String>): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    params.forEach { (pName, pType) ->
                        put(pName, JSONObject().apply { put("type", pType) })
                    }
                })
                put("required", JSONArray().apply { params.keys.forEach { put(it) } })
            })
        }
    }

    private fun handleJsonMessage(text: String) {
        val json = JSONObject(text)
        if (json.has("serverContent")) {
            val serverContent = json.getJSONObject("serverContent")
            if (serverContent.has("modelTurn")) {
                val modelTurn = serverContent.getJSONObject("modelTurn")
                val parts = modelTurn.getJSONArray("parts")
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("inlineData")) {
                        val inlineData = part.getJSONObject("inlineData")
                        val audioBase64 = inlineData.getString("data")
                        playAudio(Base64.decode(audioBase64, Base64.NO_WRAP))
                    }
                }
            }
            if (serverContent.has("interrupted")) {
                stopAudioPlayback()
                _state.value = AssistantState.LISTENING
            }
            if (serverContent.has("turnComplete")) {
                _state.value = AssistantState.LISTENING
            }
        } else if (json.has("toolCall")) {
            handleToolCall(json.getJSONObject("toolCall"))
        }
    }

    private fun handleToolCall(toolCall: JSONObject) {
        val functionCalls = toolCall.getJSONArray("functionCalls")
        val responses = JSONArray()

        for (i in 0 until functionCalls.length()) {
            val call = functionCalls.getJSONObject(i)
            val name = call.getString("name")
            val args = call.getJSONObject("args")
            val id = call.getString("id")

            val result = when (name) {
                "openApp" -> toolEngine.openApp(args.getString("packageName"))
                "searchAndCallContact" -> toolEngine.searchAndCallContact(args.getString("contactName"))
                "sendWhatsAppMessage" -> toolEngine.sendWhatsAppMessage(args.getString("contactName"), args.getString("message"))
                "sendGmail" -> toolEngine.sendGmail(args.getString("recipientEmail"), args.getString("subject"), args.getString("body"))
                else -> "I don't know how to do that yet."
            }

            responses.put(JSONObject().apply {
                put("id", id)
                put("response", JSONObject().apply { put("result", result) })
            })
        }

        val toolResponse = JSONObject().apply {
            put("tool_response", JSONObject().apply {
                put("function_responses", responses)
            })
        }
        webSocket?.send(toolResponse.toString())
    }

    private fun startRecording() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (audioRecord != null) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val audioData = if (read < buffer.size) buffer.copyOf(read) else buffer
                    val realTimeInput = JSONObject().apply {
                        put("realtime_input", JSONObject().apply {
                            put("media_chunks", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("mime_type", "audio/pcm;rate=16000")
                                    put("data", Base64.encodeToString(audioData, Base64.NO_WRAP))
                                })
                            })
                        })
                    }
                    webSocket?.send(realTimeInput.toString())
                }
            }
        }
    }

    private fun playAudio(audioData: ByteArray) {
        _state.value = AssistantState.SPEAKING
        if (audioTrack == null) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(playbackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        }
        audioTrack?.write(audioData, 0, audioData.size)
    }

    private fun stopAudioPlayback() {
        audioTrack?.stop()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null
    }

    fun stopSession() {
        webSocket?.close(1000, "User Stopped")
        webSocket = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        stopAudioPlayback()
        recordingJob?.cancel()
        _state.value = AssistantState.IDLE
    }
}
