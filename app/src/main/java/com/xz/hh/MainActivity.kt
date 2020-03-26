package com.xz.hh

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.iflytek.cloud.*
import com.iflytek.cloud.ui.RecognizerDialog
import com.iflytek.cloud.ui.RecognizerDialogListener
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import taobe.tec.jcc.JChineseConvertor
import java.util.*

/**
 * Created by xz on 2020/3/16.
 */
class MainActivity : AppCompatActivity() {
    /**
     * 初始化监听器。
     */
    private val mInitListener = InitListener { code ->
        Log.d(ContentValues.TAG, "SpeechRecognizer init() code = $code")
        if (code != ErrorCode.SUCCESS) {
            showTip("初始化失败，错误码：$code,请点击网址https://www.xfyun.cn/document/error-code查询解决方案")
        }
    }
    // 语音听写对象
    private var mIat: SpeechRecognizer? = null
    // 语音听写UI
    private var mIatDialog: RecognizerDialog? = null
    // 用HashMap存储听写结果
    private val mIatResults: HashMap<String?, String> =
        LinkedHashMap()
    private var mResultText: String? = null
    private val mSharedPreferences by lazy {
        getSharedPreferences("com.iflytek.setting", Activity.MODE_PRIVATE)
    }
    // 引擎类型
    private val mEngineType = SpeechConstant.TYPE_CLOUD
    private var language = "zh_cn"
    private val resultType = "json"

    @SuppressLint("ResourceAsColor", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener)
        mIatDialog = RecognizerDialog(this, mInitListener)
//        setParam()
        click.setOnClickListener {
            result.setText("")
            mIatDialog?.setListener(mRecognizerDialogListener)
            mIatDialog?.show()
            mIatDialog?.window?.decorView?.apply {
                findViewWithTag<TextView>("textlink").text = null
                findViewWithTag<TextView>("title").text = "傾聽中"
            }
        }
    }

    /**
     * 請求權限
     */
    @SuppressLint("ObsoleteSdkInt")
    fun requestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                val permissions = ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                if (permissions != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this, listOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.LOCATION_HARDWARE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.LOCATION_HARDWARE,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.WRITE_SETTINGS,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ).toTypedArray(), 0x0010
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * json轉為字符串
     */
    private fun String.resultForJsonParse(): String {
        val sb = StringBuilder()
        val mJSONToken = JSONTokener(this)
        val mJSONObject = JSONObject(mJSONToken)
        val mJSONArray = mJSONObject.getJSONArray("ws")
        for (i in 0 until mJSONArray.length()) {
            val items = mJSONArray.getJSONObject(i).getJSONArray("cw")
            val obj = items.getJSONObject(0)
            sb.append(obj.getString("w"))
        }
        return sb.toString()
    }

    /**
     * 結果解析
     */
    private fun printResult(results: RecognizerResult) {
        val text: String = results.resultString.resultForJsonParse()
        var sn: String? = null
        // 读取json结果中的sn字段
        try {
            val resultJson = JSONObject(results.resultString)
            sn = resultJson.optString("sn")
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        mIatResults[sn] = text
        val resultBuffer = StringBuffer()
        for (key in mIatResults.keys) {
            resultBuffer.append(mIatResults[key])
        }
        mResultText = resultBuffer.toString()
        result.setText(mResultText)
    }

    /**
     * 听写UI监听器
     */
    private val mRecognizerDialogListener: RecognizerDialogListener =
        object : RecognizerDialogListener {
            override fun onResult(
                results: RecognizerResult,
                isLast: Boolean
            ) {
                printResult(results)
            }

            /**
             * 识别回调错误.
             */
            override fun onError(error: SpeechError) {
                showTip(error.getPlainDescription(true))
            }
        }

    /**
     * toast
     */
    private fun showTip(str: String) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show()
    }

    /**
     * 参数设置
     *
     * @return
     */
    private fun setParam() {

        mIat?.apply {
            this.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType)
            // 设置返回结果格式
            this.setParameter(SpeechConstant.RESULT_TYPE, resultType)
            if (language == "zh_cn") {
                val lag = mSharedPreferences.getString(
                    "iat_language_preference",
                    "mandarin"
                )
                this.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
                // 设置语言区域
                this.setParameter(SpeechConstant.ACCENT, lag)
            } else {
                this.setParameter(SpeechConstant.LANGUAGE, language)
            }
            this.setParameter(
                SpeechConstant.VAD_BOS,
                mSharedPreferences.getString("iat_vadbos_preference", "4000")
            )
            // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
            this.setParameter(
                SpeechConstant.VAD_EOS,
                mSharedPreferences.getString("iat_vadeos_preference", "1000")
            )
            // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
            this.setParameter(
                SpeechConstant.ASR_PTT,
                mSharedPreferences.getString("iat_punc_preference", "1")
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 退出时释放连接
        mIat?.cancel()
        mIat?.destroy()
    }
}
