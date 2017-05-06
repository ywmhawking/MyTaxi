package com.dalimao.mytaxi.account;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.dalimao.corelibrary.VerificationCodeInput;
import com.dalimao.mytaxi.R;
import com.dalimao.mytaxi.common.http.IHttpClient;
import com.dalimao.mytaxi.common.http.IRequest;
import com.dalimao.mytaxi.common.http.IResponse;
import com.dalimao.mytaxi.common.http.api.API;
import com.dalimao.mytaxi.common.http.biz.BaseBizResponse;
import com.dalimao.mytaxi.common.http.impl.BaseRequest;
import com.dalimao.mytaxi.common.http.impl.BaseResponse;
import com.dalimao.mytaxi.common.http.impl.OkHttpClientImpl;
import com.dalimao.mytaxi.common.util.ToastUtil;
import com.google.gson.Gson;

import java.lang.ref.SoftReference;
import java.util.Map;


/**
 * Created by liuguangli on 17/3/5.
 */

public class SmsCodeDialog extends Dialog  {
    private static final String TAG = "SmsCodeDialog";
    private static final int SMS_SEND_SUC = 1;
    private static final int SMS_SEND_FAIL = -1 ;
    private static final int SMS_CHECK_SUC = 2;
    private static final int SMS_CHECK_FAIL = -2;
    private String mPhone;
    private Button mResentBtn;
    private VerificationCodeInput mVerificationInput;
    private View mLoading;
    private View mErrorView;
    private TextView mPhoneTv;
    private IHttpClient mHttpClient;
    private MyHandler mHandler;
    /**
     *  验证码倒计时
     */
    private CountDownTimer mCountDownTimer = new CountDownTimer(10000,1000) {
        @Override
        public void onTick(long millisUntilFinished) {

            mResentBtn.setEnabled(false);
            mResentBtn.setText(String.format(getContext()
                    .getString(R.string.after_time_resend,
                            millisUntilFinished/1000)));
        }

        @Override
        public void onFinish() {
            mResentBtn.setEnabled(true);
            mResentBtn.setText(getContext().getString(R.string.resend));
            cancel();
        }
    };

    /**
     * 接收子线程消息的 Handler
     */
    static class MyHandler extends Handler {
        // 软引用
        SoftReference<SmsCodeDialog> codeDialogRef;
        public MyHandler(SmsCodeDialog codeDialog) {
            codeDialogRef = new SoftReference<SmsCodeDialog>(codeDialog);
        }
        @Override
        public void handleMessage(Message msg) {
            SmsCodeDialog dialog = codeDialogRef.get();
            if (dialog == null) {
                return;
            }
            // 处理UI 变化
            switch (msg.what) {
                case SmsCodeDialog.SMS_SEND_SUC:

                    dialog.showSendState(true);
                break;
                case SmsCodeDialog.SMS_SEND_FAIL:
                    dialog.showSendState(false);

                break;
                case SmsCodeDialog.SMS_CHECK_SUC:
                    //验证码校验成功
                    dialog.showVerifyState(false);
                    break;
                case SmsCodeDialog.SMS_CHECK_FAIL:
                    //验证码校验失败
                    dialog.showVerifyState(false);
                    break;
            }

        }
    }


    public SmsCodeDialog(Context context, String phone) {
        this(context, R.style.Dialog);
        // 上一个界面传来的手机号
        this.mPhone = phone;
        mHttpClient = new OkHttpClientImpl();
        mHandler  = new MyHandler(this);
    }




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View root = inflater.inflate(R.layout.dialog_smscode_input, null);
        setContentView(root);
        mPhoneTv = (TextView) findViewById(R.id.phone);
        String template = getContext().getString(R.string.sending);
        mPhoneTv.setText(String.format(template, mPhone));
        mResentBtn = (Button) findViewById(R.id.btn_resend);
        mVerificationInput = (VerificationCodeInput) findViewById(R.id.verificationCodeInput);
        mLoading = findViewById(R.id.loading);
        mErrorView = findViewById(R.id.error);
        mErrorView.setVisibility(View.GONE);
        initListeners();
        requestSendSmsCode();
    }

    /**
     * 请求下发验证码
     */
    private void requestSendSmsCode() {
        new Thread() {
            @Override
            public void run() {
                String url = API.Config.getDomain() + API.GET_SMS_CODE;
                IRequest request = new BaseRequest(url);
                request.setBody("phone", mPhone);
                IResponse response = mHttpClient.get(request, false);
                Log.d(TAG, response.getData());
                if (response.getCode() == BaseResponse.STATE_OK) {
                    BaseBizResponse bizRes =
                            new Gson().fromJson(response.getData(), BaseBizResponse.class);
                    if (bizRes.getCode() == BaseBizResponse.STATE_OK) {
                        mHandler.sendEmptyMessage(SMS_SEND_SUC);
                    } else  {
                        mHandler.sendEmptyMessage(SMS_SEND_FAIL);
                    }
                } else {
                    mHandler.sendEmptyMessage(SMS_SEND_FAIL);
                }

            }
        }.start();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mCountDownTimer.cancel();

    }
    @Override
    public void dismiss() {
        super.dismiss();
    }


    public SmsCodeDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    protected SmsCodeDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }
    private void initListeners() {

        //  关闭按钮组册监听器
        findViewById(R.id.close).setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        // 重发验证码按钮注册监听器
        mResentBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resend();
            }
        });

        // 验证码输入完成监听器
        mVerificationInput.setOnCompleteListener(new VerificationCodeInput.Listener() {
            @Override
            public void onComplete(String code) {

                commit(code);
            }
        });
    }

    /**
     * 提交验证码
     * @param code
     */
    private void commit(final String code) {
        showLoading();

        // 网络请求校验验证码

        new Thread() {
            @Override
            public void run() {
                String url = API.Config.getDomain() + API.CHECK_SMS_CODE;
                IRequest request = new BaseRequest(url);
                request.setBody("phone", mPhone);
                request.setBody("code", code);
                IResponse response = mHttpClient.get(request, false);
                Log.d(TAG, response.getData());
                if (response.getCode() == BaseResponse.STATE_OK) {
                    BaseBizResponse bizRes =
                            new Gson().fromJson(response.getData(), BaseBizResponse.class);
                    if (bizRes.getCode() == BaseBizResponse.STATE_OK) {
                        mHandler.sendEmptyMessage(SMS_CHECK_SUC);
                    } else  {
                        mHandler.sendEmptyMessage(SMS_CHECK_FAIL);
                    }
                } else {
                    mHandler.sendEmptyMessage(SMS_CHECK_FAIL);
                }

            }
        }.start();
    }

    private void resend() {

        String template = getContext().getString(R.string.sending);
        mPhoneTv.setText(String.format(template, mPhone));

    }




    public void showLoading() {
        mLoading.setVisibility(View.VISIBLE);

    }


    public void showSendState(boolean suc) {

        if (suc) {
            mPhoneTv.setText(String.format(getContext().getString(R.string.sms_code_send_phone), mPhone));
            mCountDownTimer.start();
        } else {
            ToastUtil.show(getContext(),
                     getContext().getString(R.string.sms_send_fail));
            mResentBtn.setEnabled(true);
            mResentBtn.setText(getContext().getString(R.string.resend));
            cancel();
        }

    }




    public void showVerifyState(boolean suc) {
        if (!suc) {

            //提示验证码错误
            mErrorView.setVisibility(View.VISIBLE);
            mVerificationInput.setEnabled(true);
            mLoading.setVisibility(View.GONE);
        } else {

            mErrorView.setVisibility(View.GONE);
            mLoading.setVisibility(View.VISIBLE);
            //todo :检查用户是否存在


        }
    }


    public void showUserExist(boolean exist) {
        mLoading.setVisibility(View.GONE);
        mErrorView.setVisibility(View.GONE);
        dismiss();
        if (!exist) {
            // todo 用户不存在,进入注册



        } else {
            // todo 用户存在 ，进入登录


        }
    }
}
