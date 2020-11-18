package com.example.javabluetoothtestexample

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ENABLE_BT = 10 // 블루투스 활성화 상태
        private const val TAG = "MainActivity"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null // 블루투스 어댑터
    private lateinit var devices: Set<BluetoothDevice> // 블루투스 디바이스 데이터 셋

    private var bluetoothDevice: BluetoothDevice? = null // 블루투스 디바이스
    private var bluetoothSocket: BluetoothSocket? = null // 블루투스 소켓
    private lateinit var outputStream: OutputStream // 블루투스에 데이터를 출력하기 위한 출력 스트림
    private lateinit var inputStream: InputStream // 블루투스에 데이터를 입력하기 위한 입력 스트림
    private lateinit var workerThread: Thread // 문자열 수신에 사용되는 쓰레드

    private lateinit var readBuffer: ByteArray // 수신 된 문자열을 저장하기 위한 버퍼
    private var readBufferPosition = 0 // 버퍼 내 문자 저장 위치 = 0

    private lateinit var textViewReceive: TextView // 수신 된 데이터를 표시하기 위한 텍스트 뷰
    private lateinit var editTextSend: EditText // 송신 할 데이터를 작성하기 위한 에딧 텍스트
    private lateinit var buttonSend: Button // 송신하기 위한 버튼
    private lateinit var buttonPairing: Button // 블루투스 연결 버튼
    private lateinit var vwIsOn: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 각 컨테이너들의 id를 매인 xml과 맞춰준다.
        textViewReceive = findViewById<View>(R.id.textView_receive) as TextView
        editTextSend = findViewById<View>(R.id.editText_send) as EditText
        buttonSend = findViewById<View>(R.id.button_send) as Button
        buttonPairing = findViewById<View>(R.id.button_pairing) as Button
        vwIsOn = findViewById<View>(R.id.vw_ison)

        buttonSend.setOnClickListener { sendData(editTextSend.text.toString()) }
        buttonPairing.setOnClickListener { bluetoothActive() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult")
        when (requestCode) {
            REQUEST_ENABLE_BT -> if (requestCode == Activity.RESULT_OK) { // '사용'을 눌렀을 때
                selectBluetoothDevice() // 블루투스 디바이스 선택 함수 호출
            } else { // '취소'를 눌렀을 때
                Toast.makeText(this, "블루투스 연결 후 앱 이용이 가능합니다.", Toast.LENGTH_SHORT).show()
                setIsOn(false)
            }
        }
    }

    private fun bluetoothActive() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() // 블루투스 어댑터를 디폴트 어댑터로 설정
        if (bluetoothAdapter == null) { // 디바이스가 블루투스를 지원하지 않을 때
            Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show()
            setIsOn(false)
            // 여기에 처리 할 코드를 작성하세요.
        } else { // 디바이스가 블루투스를 지원 할 때
            if (bluetoothAdapter!!.isEnabled) { // 블루투스가 활성화 상태 (기기에 블루투스가 켜져있음)
                Log.d(TAG, "블루투스 활성화")
                selectBluetoothDevice() // 블루투스 디바이스 선택 함수 호출
            } else { // 블루투스가 비 활성화 상태 (기기에 블루투스가 꺼져있음)
                // 블루투스를 활성화 하기 위한 다이얼로그 출력
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

                // 선택한 값이 onActivityResult 함수에서 콜백된다.
                Log.d(TAG, "startActivityForResult")
                startActivityForResult(intent, REQUEST_ENABLE_BT)
            }
        }
    }

    private fun selectBluetoothDevice() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "블루투스 연결 후 앱 이용이 가능합니다.", Toast.LENGTH_SHORT).show()
            setIsOn(false)
            return
        }

        // 이미 페어링 되어있는 블루투스 기기를 찾습니다.
        devices = bluetoothAdapter!!.bondedDevices

        // 페어링 된 디바이스의 크기를 저장
        val pariedDeviceCount = devices.size

        // 페어링 되어있는 장치가 없는 경우
        if (pariedDeviceCount == 0) {
            Toast.makeText(this, "블루투스에 연결했던 기기가 없습니다. 블루투스 기기 연결 후 이용해주세요.", Toast.LENGTH_SHORT).show()
            setIsOn(false)
            // 페어링을 하기위한 함수 호출
        } else { // 페어링 되어있는 장치가 있는 경우
            // 디바이스를 선택하기 위한 다이얼로그 생성
            val builder = AlertDialog.Builder(this)
            builder.setTitle("페어링 되어있는 블루투스 디바이스 목록")

            // 페어링 된 각각의 디바이스의 이름과 주소를 저장
            val list: MutableList<String> = ArrayList()

            // 모든 디바이스의 이름을 리스트에 추가
            for (bluetoothDevice in devices) {
                list.add(bluetoothDevice.name)
            }
            list.add("취소")

            // List를 CharSequence 배열로 변경
            val charSequences = list.toTypedArray<CharSequence>()
            list.toTypedArray<CharSequence>()

            // 해당 아이템을 눌렀을 때 호출 되는 이벤트 리스너
            builder.setItems(charSequences) { dialog, which -> // 해당 디바이스와 연결하는 함수 호출
                val data = charSequences.getOrNull(which)

                if (data != null) {
                    connectDevice(data.toString())
                }
            }

            // 뒤로가기 버튼 누를 때 창이 안닫히도록 설정
            builder.setCancelable(false)

            // 다이얼로그 생성
            val alertDialog = builder.create()
            alertDialog.show()
        }
    }

    private fun connectDevice(deviceName: String) {

        // 페어링 된 디바이스들을 모두 탐색
        for (tempDevice in devices) {
            // 사용자가 선택한 이름과 같은 디바이스로 설정하고 반복문 종료
            if (deviceName == tempDevice.name) {
//                if (bluetoothDevice?.name == tempDevice.name) { return }
                bluetoothDevice = tempDevice
                Log.d(TAG, "연결 기기 선택")
                break
            }
        }

        if (bluetoothDevice == null) {
            Toast.makeText(this, "블루투스 연결에 실패하였습니다.", Toast.LENGTH_SHORT).show()
            setIsOn(false)
            return
        }

        // UUID 생성
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

        // Rfcomm 채널을 통해 블루투스 디바이스와 통신하는 소켓 생성
        try {
//            if (bluetoothSocket != null) {
//                if (bluetoothSocket!!.isConnected) {
//                    // TODO: - 테스트 꼭 해볼 것
//                    bluetoothSocket?.close()
//                }
//            }

            val socket: BluetoothSocket = bluetoothDevice!!.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket = socket
            socket.connect()
            Log.d(TAG, "connect")

            // 데이터 송,수신 스트림을 얻어옵니다.
            outputStream = socket.outputStream
            inputStream = socket.inputStream
            Log.d(TAG, "get stream")

            if (bluetoothSocket!!.isConnected) {
                setIsOn(true)
                receiveData()
            } else {
                setIsOn(false)
            }
        } catch (e: IOException) {
            Toast.makeText(this, "블루투스 연결에 실패하였습니다.", Toast.LENGTH_SHORT).show()
            setIsOn(false)
            e.printStackTrace()
        }
    }

    // TODO: - 테스트 해보고 코드 검증할 것
    private fun receiveData() {
        val handler = Handler()

        // 데이터를 수신하기 위한 버퍼를 생성
        readBufferPosition = 0
        readBuffer = ByteArray(1024)

        Log.d(TAG, "데이터 수신용 쓰레드 생성")
        // 데이터를 수신하기 위한 쓰레드 생성
        workerThread = Thread(Runnable {
            while (Thread.currentThread().isInterrupted) {
                try {
                    Log.d(TAG, "데이터 수신")
                    // 데이터를 수신했는지 확인합니다.
                    val byteAvailable = inputStream.available()

                    // 데이터가 수신 된 경우
                    if (byteAvailable > 0) {

                        // 입력 스트림에서 바이트 단위로 읽어 옵니다.
                        val bytes = ByteArray(byteAvailable)
                        inputStream.read(bytes)

                        // 입력 스트림 바이트를 한 바이트씩 읽어 옵니다.
                        for (i in 0 until byteAvailable) {
                            val tempByte = bytes[i]

                            // 개행문자를 기준으로 받음(한줄)
                            if (tempByte == '\n'.toByte()) {
                                // readBuffer 배열을 encodedBytes로 복사
                                val encodedBytes = ByteArray(readBufferPosition)
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.size)

                                // 인코딩 된 바이트 배열을 문자열로 변환
                                val text = String(encodedBytes, Charsets.UTF_8)
                                readBufferPosition = 0
                                handler.post { // 텍스트 뷰에 출력
                                    textViewReceive.append(text + "\n")
                                }
                            } // 개행 문자가 아닐 경우
                            else {
                                readBuffer[readBufferPosition++] = tempByte
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                try {
                    // 1초마다 받아옴
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        })
        workerThread.start()
    }

    // TODO: - 테스트 해보고 코드 검증할 것
    private fun sendData(text: String) {
        Log.d(TAG, "데이터 전송")
        // 문자열에 개행문자("\n")를 추가해줍니다.
        var text = text
        text += "\n"
        try {
            // 데이터 송신
            outputStream.write(text.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setIsOn(flag: Boolean) {
        if (flag) {
            vwIsOn.setBackgroundColor(Color.GREEN)
        } else {
            vwIsOn.setBackgroundColor(Color.RED)
        }
    }
}