package sz.co.seke.seke

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.DhcpInfo
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.widget.Toast
import com.budiyev.android.codescanner.*
import com.github.kittinunf.fuel.Fuel
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import sz.co.seke.seke.R.string.*
import java.io.BufferedReader
import java.io.FileReader
import java.net.NetworkInterface
import java.net.NetworkInterface.getNetworkInterfaces
import java.net.SocketException
import android.text.format.Formatter.formatIpAddress
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.text.format.Formatter
import org.json.JSONArray


val MY_PERMISSIONS_REQUEST_CAMERA:Int = 1000;


class MainActivity : AppCompatActivity() {
    private lateinit var codeScanner: CodeScanner
    private var itemList: MutableList<Item> = mutableListOf()
    private lateinit var adapter: ItemsAdapter
    private var serverIp = ""

    public fun removeItem(position:Int){
        itemList.removeAt(position)
        adapter.notifyDataSetChanged()
        var total = 0F;
        for (i in 0..itemList.size-1){
            total+=itemList[i].price
        }

        total_text_view.text = "Total: E $total"
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_CAMERA -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.setProperty("java.net.preferIPv4Stack", "true");
        setContentView(R.layout.activity_main)

        getServerIp()


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e("Permission","No")
            // Permission is not granted
        }
        

        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA),
                    MY_PERMISSIONS_REQUEST_CAMERA)

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {

            
            // Permission has already been granted
        }

        codeScanner = CodeScanner(this, scanner_view)

        // Parameters (default values)
        codeScanner.camera = CodeScanner.CAMERA_BACK // or CAMERA_FRONT or specific camera id
        codeScanner.formats = CodeScanner.ALL_FORMATS // list of type BarcodeFormat,
        // ex. listOf(BarcodeFormat.QR_CODE)
        codeScanner.autoFocusMode = AutoFocusMode.CONTINUOUS // or CONTINUOUS
        codeScanner.scanMode = ScanMode.SINGLE // or CONTINUOUS or PREVIEW
        codeScanner.isAutoFocusEnabled = false // Whether to enable auto focus or not
        codeScanner.isFlashEnabled = false // Whether to enable flash or not

        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)

        val actionsConfig: MutableList<String> = mutableListOf()
        adapter = ItemsAdapter(itemList,this)

        items_recycler_view.setHasFixedSize(true)
        items_recycler_view.layoutManager = LinearLayoutManager(this)
        items_recycler_view.adapter = adapter

        scan_btn.setOnClickListener {
            scan()
            scanner_view.visibility = View.VISIBLE
            main_ly.visibility = View.GONE
        }

        pay_btn.setOnClickListener {
            startActivity(Intent(MainActivity@this,ReceitListActivity::class.java))
        }

request_payement.setOnClickListener {
    val items = JSONArray()
    for (i in 0..itemList.size-1){
        items.put(itemList[i].code)
    }
    Fuel.put("$serverIp/item")
        .jsonBody(items.toString())
        .response{request, response, result ->
            Log.e("Payed",response.data.toString())
            val intent = Intent(this,ReceitListActivity::class.java)
            if(response.statusCode==200){
                intent.putExtra("receipt",String(response.data))
                startActivity(intent)
            }else{
                Toast.makeText(this,"Did not connect",Toast.LENGTH_LONG).show()
            }

        }
}
    }

    fun scan(){

        codeScanner.startPreview()

        // Callbacks
        codeScanner.decodeCallback = DecodeCallback {
            runOnUiThread {
                Toast.makeText(this, "Scan result: ${it.text}", Toast.LENGTH_LONG).show()
                scanner_view.visibility = View.GONE
                main_ly.visibility = View.VISIBLE
                codeScanner.stopPreview()
                Log.e("Server ip",serverIp)
                Fuel.get("${serverIp}/item/${it.text}")
                    .response{ request, response, result ->
                        println(request)
                        println(response)
                        if(response.statusCode == 200){
                            val itemObject = JSONObject(String(response.data))
                            Log.e("Itemobject",itemObject.toString())
                            itemList.add(Item(itemObject.getString("bar_code"),itemObject.getString("name"),itemObject.get("price").toString().toFloat()))
                            adapter.notifyDataSetChanged()
                            var total = 0F;
                            for (i in 0..itemList.size-1){
                                total+=itemList[i].price
                            }
                            total_text_view.text = "Total: E $total"
                        }else{
                            Toast.makeText(this,"There was an error "+response.statusCode,Toast.LENGTH_LONG).show()
                        }

                    }
            }
        }
        codeScanner.errorCallback = ErrorCallback { // or ErrorCallback.SUPPRESS
            runOnUiThread {
                Log.e("Error",it.toString())
                Toast.makeText(this, "Camera initialization error: ${it.message}",
                    Toast.LENGTH_LONG).show()
            }
        }

    }

    override fun onBackPressed() {
        if (codeScanner.isPreviewActive){
            codeScanner.stopPreview()
            scanner_view.visibility = View.GONE;
            main_ly.visibility = View.VISIBLE
        }else{
            super.onBackPressed()
        }
    }
    
    fun getServerIp(){
        val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiMgr.connectionInfo
        val ip = wifiInfo.ipAddress
        val ipAddress = Formatter.formatIpAddress(ip)

        val base = ipAddress.substring(0,ipAddress.lastIndexOf("."))
        for(i in 1..254){
            Fuel.get("http://$base.$i:3002/ping")
                .response{ request, response, result ->
                    if(response.statusCode == 200){
                        Log.e("HOST",response.url.host)
                        serverIp = "http://${response.url.host}:3002"
                    }
                }
        }
    }

}
