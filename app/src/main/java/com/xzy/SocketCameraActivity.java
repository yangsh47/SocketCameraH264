package com.xzy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class SocketCameraActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener {

	/*MediaCodec参数*/
	String path = Environment.getExternalStorageDirectory() + "/easy.h264";
	String testPath = Environment.getExternalStorageDirectory() + "/test.h264";

	int width = 640, height = 480;
	int framerate = 30, bitrate = 1000 * width * height * framerate / 20;;
	int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
	MediaCodec mMediaCodec;
	NV21Convertor mConvertor;
//	boolean started = false;

	/*Socket参数*/
	private SurfaceView mSurfaceView = null; // SurfaceView对象：(视图组件)视频显示
	private SurfaceHolder mSurfaceHolder = null; // SurfaceHolder对象：(抽象接口)SurfaceView支持类
	private Camera mCamera = null; // Camera对象，相机预览

	/**服务器地址*/
	private String pUsername="XZY";
	/**服务器地址*/
	private String serverUrl="192.168.1.100";
	/**服务器端口*/
	private int serverPort=8888;
	/**视频刷新间隔*/
	private int VideoPreRate=1;
	/**当前视频序号*/
	private int tempPreRate=0;
	/**视频质量*/
	private int VideoQuality=85;


	/**发送视频宽度比例*/
	private float widthRatio=1;
	/**发送视频高度比例*/
	private float heightRatio=1;
	
	/**视频格式索引*/
	private int VideoFormatIndex=0;
	/**是否发送视频*/
	private boolean startSendVideo=false;
	/**是否连接主机*/
	private boolean connectedServer=false;

	private Button myBtn01, myBtn02;

	/**
	 * Called when the activity is first created.
	 * */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		//禁止屏幕休眠
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mSurfaceView = (SurfaceView) findViewById(R.id.camera_preview);
		mSurfaceView.getHolder().addCallback(this);
		mSurfaceView.getHolder().setFixedSize(getResources().getDisplayMetrics().widthPixels,
				getResources().getDisplayMetrics().heightPixels);
		myBtn01=(Button)findViewById(R.id.button1);
		myBtn02=(Button)findViewById(R.id.button2);

		initMediaCodec();
		mSurfaceView.setOnClickListener(this);
		//开始连接主机按钮
		myBtn01.setOnClickListener(this);

		myBtn02.setEnabled(false);
		myBtn02.setOnClickListener(this);
	}

	/**
	 * 初始化MediaCodec
	 * */
	private void initMediaCodec() {
		int dgree = getDgree();

		EncoderDebugger debugger = EncoderDebugger.debug(getApplicationContext(), width, height);
		mConvertor = debugger.getNV21Convertor();
		try {
			mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
			MediaFormat mediaFormat;
			if (dgree == 0) {
				mediaFormat = MediaFormat.createVideoFormat("video/avc", height, width);
			} else {
				mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
			}
			mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
			mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
			mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
					debugger.getEncoderColorFormat());
			mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
			mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			mMediaCodec.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onStart()//重新启动的时候
	{
		mSurfaceHolder = mSurfaceView.getHolder(); // 绑定SurfaceView，取得SurfaceHolder对象
		mSurfaceHolder.addCallback(this); // SurfaceHolder加入回调接口
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);// 设置显示器类型，setType必须设置

		//读取配置文件
		SharedPreferences preParas = PreferenceManager.getDefaultSharedPreferences(SocketCameraActivity.this);
		pUsername=preParas.getString("Username", "XZY");
		serverUrl=preParas.getString("ServerUrl", "192.168.0.100");
		String tempStr=preParas.getString("ServerPort", "8888");
		serverPort=Integer.parseInt(tempStr);
		tempStr=preParas.getString("VideoPreRate", "1");
		VideoPreRate=Integer.parseInt(tempStr);
		tempStr=preParas.getString("VideoQuality", "85");
		VideoQuality=Integer.parseInt(tempStr);
		tempStr=preParas.getString("widthRatio", "100");
		widthRatio=Integer.parseInt(tempStr);
		tempStr=preParas.getString("heightRatio", "100");
		heightRatio=Integer.parseInt(tempStr);
		widthRatio=widthRatio/100f;
		heightRatio=heightRatio/100f;

		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		try{
			mCamera = Camera.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 初始化摄像头
	 * */
	private boolean createCamera(SurfaceHolder surfaceHolder) {
		try {
			Camera.Parameters parameters = mCamera.getParameters();
			int[] max = determineMaximumSupportedFramerate(parameters);
			Camera.CameraInfo camInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(mCameraId, camInfo);
			int cameraRotationOffset = camInfo.orientation;
			int rotate = (360 + cameraRotationOffset - getDgree()) % 360;
			parameters.setRotation(rotate);
			parameters.setPreviewFormat(ImageFormat.NV21);
//            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
			List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
			parameters.setPreviewSize(width, height);
			parameters.setPreviewFpsRange(max[0], max[1]);
			mCamera.setParameters(parameters);
//            mCamera.cancelAutoFocus();// 2如果要实现连续的自动对焦，这一句必须加上
			//实现自动对焦
			mCamera.autoFocus(null);
			int displayRotation;
			displayRotation = (cameraRotationOffset - getDgree() + 360) % 360;
			mCamera.setDisplayOrientation(displayRotation);
			mCamera.setPreviewDisplay(surfaceHolder);
			mCamera.startPreview();
			return true;
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String stack = sw.toString();
			Toast.makeText(this, stack, Toast.LENGTH_LONG).show();
			destroyCamera();
			e.printStackTrace();
			return false;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		try{
			if (mCamera != null) {
				mCamera.setPreviewCallback(null); // ！！这个必须在前，不然退出出错
				mCamera.stopPreview();
				mCamera.release();
				mCamera = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		// TODO Auto-generated method stub
		if (mCamera == null) {
			return;
		}
		createCamera(mSurfaceHolder);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		if(null==mCamera){
			mSurfaceHolder = holder;
			mCamera=Camera.open(mCameraId);
			createCamera(mSurfaceHolder);
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		stopPreview();
		destroyCamera();
		if (null != mCamera) {
			mCamera.setPreviewCallback(null); // ！！这个必须在前，不然退出出错
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
		byte[] mPpsSps = new byte[0];

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
			//如果没有指令传输视频，就先不传
			if (data == null) {
				return;
			}
			if(!startSendVideo)
				return;
			if(tempPreRate<VideoPreRate){
				tempPreRate++;
				return;
			}
			tempPreRate=0;
			//取得输入输出buffer的调用
			ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
			ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
			byte[] dst = new byte[data.length];
			Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
			if (getDgree() == 0) {
				dst = Util.rotateNV21Degree90(data, previewSize.width, previewSize.height);
			} else {
				dst = data;
			}

			try {
				//取得输入buffer的索引
				int bufferIndex = mMediaCodec.dequeueInputBuffer(5000000);
				if (bufferIndex >= 0) {
					inputBuffers[bufferIndex].clear();
					//将数据传入输入buffer
					mConvertor.convert(dst, inputBuffers[bufferIndex]);
					//把buffer交还给编码器
					mMediaCodec.queueInputBuffer(bufferIndex, 0,
							inputBuffers[bufferIndex].position(),
							System.nanoTime() / 1000, 0);
					MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
					//取得输出buffer的索引
					int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
					List<byte[]> frameOutData = new ArrayList<byte[]>();
					while (outputBufferIndex >= 0) {
						ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
						byte[] outData = new byte[bufferInfo.size];
						outputBuffer.get(outData);
						//记录pps和sps
						if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 103) {
							mPpsSps = outData;
						} else if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 101) {
							//在关键帧前面加上pps和sps数据
							byte[] iframeData = new byte[mPpsSps.length + outData.length];
							System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
							System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
							outData = iframeData;
						}

						Util.save(outData, 0, outData.length, path, true);

						frameOutData.add(outData);

						mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
						outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
					}

					ByteArrayOutputStream outstream = new ByteArrayOutputStream();
					outstream.write(getAllByte(frameOutData), 0, getAllByte(frameOutData).length);
					outstream.flush();

					//如果有信息启用线程将每帧数据发送出去，如果没信息就不发
					if (frameOutData.size()!=0) {
						Thread th = new MySendFileThread(outstream, pUsername, serverUrl, serverPort);
						Util.save(getAllByte(frameOutData), 0, getAllByte(frameOutData).length, testPath, true);
						th.start();
					}

				} else {
					Log.e("easypusher", "No buffer available !");
				}
			} catch (Exception e) {
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				String stack = sw.toString();
				Log.e("save_log", stack);
				e.printStackTrace();
			} finally {
				mCamera.addCallbackBuffer(dst);
			}
		}

	};

	/**创建菜单*/
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0,0,0,"系统设置");
		menu.add(0,1,1,"关于程序");
		menu.add(0,2,2,"退出程序");
		return super.onCreateOptionsMenu(menu);
	}

	/**菜单选中时发生的相应事件*/
	public boolean onOptionsItemSelected(MenuItem item)
	{
		super.onOptionsItemSelected(item);//获取菜单
		switch(item.getItemId())//菜单序号
		{
			case 0:
				//系统设置
			{
				Intent intent=new Intent(this,SettingActivity.class);
				startActivity(intent);
			}
			break;
			case 1://关于程序
			{
				new AlertDialog.Builder(this)
						.setTitle("关于本程序")
						.setMessage("本程序由武汉大学水利水电学院肖泽云设计、编写。\nEmail：xwebsite@163.com")
						.setPositiveButton
								(
										"我知道了",
										new DialogInterface.OnClickListener()
										{
											@Override
											public void onClick(DialogInterface dialog, int which)
											{
											}
										}
								)
						.show();
			}
			break;
			case 2://退出程序
			{
				//杀掉线程强制退出
				android.os.Process.killProcess(android.os.Process.myPid());
			}
			break;
		}
		return true;
	}

	/*获得角度*/
	private int getDgree() {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break; // Natural orientation
			case Surface.ROTATION_90:
				degrees = 90;
				break; // Landscape left
			case Surface.ROTATION_180:
				degrees = 180;
				break;// Upside down
			case Surface.ROTATION_270:
				degrees = 270;
				break;// Landscape right
		}
		return degrees;
	}

	/*确定最大FPS*/
	public int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
		int[] maxFps = new int[]{0, 0};
		List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
		for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
			int[] interval = it.next();
			if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
				maxFps = interval;
			}
		}
//		Toast.makeText(getApplicationContext(), maxFps[0]+" "+maxFps[1],Toast.LENGTH_SHORT).show();;
		return maxFps;
	}

	/**
	 * 开启预览
	 */
	public synchronized void startPreview() {
		if (mCamera != null) {
			mCamera.startPreview();
			int previewFormat = mCamera.getParameters().getPreviewFormat();
			Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
			int size = previewSize.width * previewSize.height
					* ImageFormat.getBitsPerPixel(previewFormat)
					/ 8;
			mCamera.addCallbackBuffer(new byte[size]);
			mCamera.setPreviewCallback(previewCallback);
//			started = true;

		}
	}

	/**
	 * 停止预览
	 */
	public synchronized void stopPreview() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.setPreviewCallbackWithBuffer(null);
//			started = false;

		}
	}

	/**
	 * 销毁Camera
	 */
	protected synchronized void destroyCamera() {
		if (mCamera != null) {
			mCamera.stopPreview();
			try {
				mCamera.release();
			} catch (Exception e) {

			}
			mCamera = null;
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.button1:
				if(connectedServer){//停止连接主机，同时断开传输
					startSendVideo=false;
					connectedServer=false;
					myBtn02.setEnabled(false);
					myBtn01.setText("开始连接");
					myBtn02.setText("开始传输");
					//断开连接
					Thread th = new MySendCommondThread("PHONEDISCONNECT|"+pUsername+"|");
					th.start();
				}
				else//连接主机
				{
					//启用线程发送命令PHONECONNECT
					Thread th = new MySendCommondThread("PHONECONNECT|"+pUsername+"|");
					th.start();
					connectedServer=true;
					myBtn02.setEnabled(true);
					myBtn01.setText("停止连接");
				}
				break;
			case R.id.button2:
				if(startSendVideo)//停止传输视频
				{
					startSendVideo=false;
					myBtn02.setText("开始传输");
					stopPreview();

				}
				else{ // 开始传输视频
					startSendVideo=true;
					myBtn02.setText("停止传输");
					startPreview();
				}
				break;
			case R.id.camera_preview:
				mCamera.autoFocus(new Camera.AutoFocusCallback(){

					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						// TODO 自动生成的方法存根

					}

				});
				break;
		}
	}

	/**
	 * 获得所有byte[]
	 * */
	public byte[] getAllByte (List<byte[]> b) {
		int num = 0;
		for(int i=0;i<b.size();i++){
			num += b.get(i).length;
		}
		byte[] mByte = new byte[num];
		for(int i=0;i<b.size();i++){
			if (i==0)
				System.arraycopy(b.get(i), 0, mByte, 0, b.get(i).length);
			else
				System.arraycopy(b.get(i), 0, mByte, b.get(i-1).length, b.get(i).length);
		}
		return  mByte;
	}

	/**发送命令线程*/
	class MySendCommondThread extends Thread{
		private String commond;
		public MySendCommondThread(String commond){
			this.commond=commond;
		}
		public void run(){
			//实例化Socket
			try {
				Socket socket=new Socket(serverUrl,serverPort);
				PrintWriter out = new PrintWriter(socket.getOutputStream());
				out.println(commond);
				out.flush();
			} catch (UnknownHostException e) {
			} catch (IOException e) {
			}
		}
	}

	/**发送文件线程*/
	class MySendFileThread extends Thread{
		private String username;
		private String ipname;
		private int port;
		private byte byteBuffer[] = new byte[1024];
		private OutputStream outsocket;
		private ByteArrayOutputStream myoutputstream;

		public MySendFileThread(ByteArrayOutputStream myoutputstream,String username,String ipname,int port){
			this.myoutputstream = myoutputstream;
			this.username=username;
			this.ipname = ipname;
			this.port=port;
			try {
				myoutputstream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		public void run() {
			try{
				//将图像数据通过Socket发送出去
				Socket tempSocket = new Socket(ipname, port);
				outsocket = tempSocket.getOutputStream();
				//写入头部数据信息
				String msg=java.net.URLEncoder.encode("PHONEVIDEO|"+username+"|","utf-8");
				byte[] buffer= msg.getBytes();
				outsocket.write(buffer);

				ByteArrayInputStream inputstream = new ByteArrayInputStream(myoutputstream.toByteArray());
				int amount;
				//把输入流数据填入byteBuffer
				while ((amount = inputstream.read(byteBuffer)) != -1) {
					outsocket.write(byteBuffer, 0, amount);
				}
				myoutputstream.flush();
				myoutputstream.close();
				tempSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}