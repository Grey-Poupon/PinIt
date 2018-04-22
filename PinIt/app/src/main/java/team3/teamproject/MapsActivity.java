package team3.teamproject;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.StrictMode;
import android.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.Spinner;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;

import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.maps.android.heatmaps.Gradient;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, AdapterView.OnItemSelectedListener, OnChartGestureListener, OnChartValueSelectedListener {

    private LineChart mChart;
    private DialogFragment logoutDialog;

    private GoogleMap mMap;
    private TileOverlay mOverlay;
    private HeatmapTileProvider mProvider;
    private OverlayState overlayState;
    private HashMap<String,JsonSensorMessage> sensors;
    private static final String TAG = MapsActivity.class.getSimpleName();

    private LatLng startlocation = new LatLng(54.973701, -1.624397);
    private final int maxZoom = 15;
    private final int minZoom = 13;
    private final int radiusBlur = 35;
    private final LatLngBounds mapBounds = new LatLngBounds(
            new LatLng(54.85, -1.7), new LatLng(55.07, -1.52));

    private int[] colours = {
            Color.rgb(152, 236, 220),
            Color.rgb(75, 205, 179),
            Color.rgb(30, 148, 126),
            Color.rgb(0, 91, 73)};

    private float[] startPoints = {0.1f, 0.4f, 0.7f, 1f};

    /**
     * List to manage the forum markers, add location and title to add a new Marker
     */
    private List<Marker> forumMarkers = new ArrayList<Marker>();

    private Toolbar appToolbar;
    private Spinner heatmapTypeSpinner;

    public MapsActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);


        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        // get sensors from server
        sensors = createSensorMap();

        logoutDialog = new LogoutDialog();
        appToolbar = (Toolbar) findViewById(R.id.toolbar3);
        // Pollution selection
        heatmapTypeSpinner = (Spinner) findViewById(R.id.heatmapType);

        ArrayAdapter<CharSequence> heatmapTypeAdapter = ArrayAdapter.createFromResource(this,
                R.array.polutionTypes, R.layout.custom_spinner_item);
        heatmapTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        heatmapTypeSpinner.setAdapter(heatmapTypeAdapter);
        heatmapTypeSpinner.setOnItemSelectedListener(this);

        Pin.addPins(getAllPins());
        //Tyler chart stuff
        mChart = (LineChart) findViewById(R.id.lineChart);
        mChart.setOnChartGestureListener(MapsActivity.this);
        mChart.setOnChartValueSelectedListener(MapsActivity.this);


        ArrayList<Entry> yValues = new ArrayList<>();

        yValues.add(new Entry(0, 20f));
        yValues.add(new Entry(1, 30f));
        yValues.add(new Entry(2, 60f));
        yValues.add(new Entry(3, 10f));
        yValues.add(new Entry(4, 50f));
        yValues.add(new Entry(5, 35f));
        LineDataSet set1 = new LineDataSet(yValues, "set 1");
        set1.setFillAlpha(110);
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(set1);

        LineData data = new LineData(dataSets);

        mChart.setData(data);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        try {
            // Customise the styling of the base map using a JSON object defined
            // in a raw resource file.
            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.map_style_json));

            if (!success) {
                Log.e(TAG, "Style parsing failed.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e(TAG, "Can't find style. Error: ", e);
        }
        startLocation(startlocation, 15);
        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        // mMap.getUiSettings().setZoomGesturesEnabled(false);
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.getUiSettings().setTiltGesturesEnabled(false);
        mMap.setMaxZoomPreference(maxZoom);
        mMap.setMinZoomPreference(minZoom);
        mMap.setLatLngBoundsForCameraTarget(mapBounds);
        setupForumMarkers(mMap);
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (marker.getTitle() != null) {
                    Intent forum = new Intent(MapsActivity.this, PostListActivity.class);
                    forum.putExtra("PinID", (String) marker.getTag());
                    forum.putExtra("title", marker.getTitle());
                    startActivity(forum);
                }
                return true;
            }
        });


        // Uncomment to view all sensors
        //SensorPlacement();
    }

    private void setupForumMarkers(GoogleMap map) {
        for (Pin pin : Pin.allPins) {
            Marker text = map.addMarker(
                    new MarkerOptions()
                            .position(pin.getLongLat())
                            .title(pin.getName())
                            .icon(createText(pin.getName())));
            text.setAnchor(0.5f, 0f);

            Marker m = map.addMarker(
                    new MarkerOptions()
                            .position(pin.getLongLat())
                            .title(pin.getName()));
            m.setTag(pin.getID());
            forumMarkers.add(m);
        }
    }

    /**
     * move the camera when screen launched
     * Created by Petr Makarov modified by Rheyn Scholtz
     */
    private void startLocation(LatLng lat, int zoom) {
        CameraUpdate update = CameraUpdateFactory.newLatLngZoom(lat, zoom);
        mMap.moveCamera(update);
    }

    // Rheyn Scholtz

    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String selectedPollution = heatmapTypeSpinner.getSelectedItem().toString();
        if (selectedPollution.equals(OverlayState.CO.toString()))  {
            UpdateHeatMap(OverlayState.CO);
        }
        else if (selectedPollution.equals(OverlayState.Humidity.toString())) {
            UpdateHeatMap(OverlayState.Humidity);
        }
        else if (selectedPollution.equals(OverlayState.NO.toString())) {
            UpdateHeatMap(OverlayState.NO);
        }
        else if (selectedPollution.equals(OverlayState.NO2.toString())) {
            UpdateHeatMap(OverlayState.NO2);
        }
        else if (selectedPollution.equals(OverlayState.Sound.toString())) {
            UpdateHeatMap(OverlayState.Sound);
        }
        else if (selectedPollution.equals(OverlayState.Temperature.toString())) {
            UpdateHeatMap(OverlayState.Temperature);
        }
        else if (selectedPollution.equals(OverlayState.Wind_speed.toString())) {
            UpdateHeatMap(OverlayState.Wind_speed);
        }
        else if (selectedPollution.equals(OverlayState.NOX.toString())) {
            UpdateHeatMap(OverlayState.NOX);
        }
        else if (selectedPollution.equals(OverlayState.O3.toString())) {
            UpdateHeatMap(OverlayState.O3);
        }
        else if (selectedPollution.equals(OverlayState.Pressure.toString())) {
            UpdateHeatMap(OverlayState.Pressure);
        }
        else if (selectedPollution.equals(OverlayState.Rain_Accumulation.toString())) {
            UpdateHeatMap(OverlayState.Rain_Accumulation);
        }
        else if (selectedPollution.equals(OverlayState.Rain_Fall.toString())) {
            UpdateHeatMap(OverlayState.Rain_Fall);
        }
        else if (selectedPollution.equals(OverlayState.Sewage_Level.toString())) {
            UpdateHeatMap(OverlayState.Sewage_Level);
        }
        else if (selectedPollution.equals(OverlayState.Solar_Diffuseradiation.toString())) {
            UpdateHeatMap(OverlayState.Solar_Diffuseradiation);
        }
        else if (selectedPollution.equals(OverlayState.Visiblity.toString())) {
            UpdateHeatMap(OverlayState.Visiblity);
        }
        else {
            Log.e("UNKNOWN POLLUTION TYPE", "" + selectedPollution.toString());
        }
    }



    public void onNothingSelected(AdapterView<?> adapterView) {
        return;
    }

    private void UpdateHeatMap(OverlayState pollutionType) {
        // Get all sensor data to place on the heatmap
        List<JsonSensorData> allRelivantSensorData = getSensorsFromType(pollutionType);

        if (allRelivantSensorData == null) {
            Log.e("SENSOR ERROR", "No data is found");
            return;
        }

        Log.e("PROGRESS", "Got all sensor data");

        LatLng latLng = null;
        for(int i =0;i<allRelivantSensorData.size();i++){

            String id = Integer.toString(allRelivantSensorData.get(i).getSensorId());
            JsonSensorMessage sensor = sensors.get(id);
            // any sensor data that doesnt match the sensor we have dont use
            if(sensor!=null) {
                latLng = sensor.getLatLng();
            }
            allRelivantSensorData.get(i).applyLocation(latLng);
        }

        Log.e("PROGRESS", "All data processed");

        // Find the smallest and largest value
        double min = allRelivantSensorData.get(0).value;
        double max = allRelivantSensorData.get(0).value;

        for (JsonSensorData sensorData : allRelivantSensorData) {
            if (sensorData.value < min) {
                min = sensorData.value;
            }
            if (sensorData.value > max) {
                max = sensorData.value;
            }
        }

        List<WeightedLatLng> mapData = new ArrayList<>();


        for (JsonSensorData sensorData : allRelivantSensorData) {
            LatLng location = sensorData.getLatLng();
            if (location != null) {
                mapData.add(new WeightedLatLng(location, (sensorData.getValue() - min) / (max - min)));
            } else {
                Log.e("NO LOCATION FOUND", "No location found for sensor:" + sensorData.getSensorId());
            }
        }

        placeDataOnMap(mapData);
    }

    // Rheyn Scholtz
    private void placeDataOnMap(List<WeightedLatLng> heatmapData) {

        if ((heatmapData.size() < 1) || (heatmapData == null)) {
            return;
        }

        mProvider = new HeatmapTileProvider.Builder().weightedData(heatmapData).radius(radiusBlur).gradient(new Gradient(colours, startPoints)).build();
        mProvider.setRadius(40);

        if (mOverlay != null) {
            mOverlay.remove();
        }
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }

    public List<JsonSensorData> getSensorsFromType(OverlayState type){
        String urlPath = "https://duffin.co/uo/getData.php?property=";
        if (type == OverlayState.Sound) {
            urlPath += "Sound";
        }
        else if (type == OverlayState.Temperature) {
            urlPath += "Temperature";
        }
        else if (type == OverlayState.Humidity) {
            urlPath += "Humidity";
        }
        else if (type == OverlayState.NO2) {
            urlPath += "NO2";
        }
        else if (type == OverlayState.NO) {
            urlPath += "NO";
        }
        else if (type == OverlayState.CO) {
            urlPath += "CO";
        }
        else if (type == OverlayState.Wind_speed) {
            urlPath += "Windspeed";
        }
        else if (type == OverlayState.NOX) {
            urlPath += "NOX";
        }
        else if (type == OverlayState.O3) {
            urlPath += "O3";
        }
        else if (type == OverlayState.Pressure) {
            urlPath += "Pressure";
        }
        else if (type == OverlayState.Rain_Accumulation) {
            urlPath += "RainAccumulation";
        }
        else if (type == OverlayState.Rain_Fall) {
            urlPath += "RainFall";
        }
        else if (type == OverlayState.Sewage_Level) {
            urlPath += "SewageLevel";
        }
        else if (type == OverlayState.Solar_Diffuseradiation) {
            urlPath += "SolarDiffuseradiation";
        }
        else if (type == OverlayState.Visiblity) {
            urlPath += "Visiblity";
        }
        else {
            Log.e("Overlay state error", "" + overlayState.toString() + " Not recognised");
        }

        //Will be replaced with graph functionality later

        int heatmapIndex = getNewestIndex();
        if (heatmapIndex == -1) {
            return null;
        }

        List<JsonSensorData> listOfSensorData = getSensorDataFromDatabase(urlPath, heatmapIndex);

        if ((listOfSensorData == null) || (listOfSensorData.size() == 0)) {
            Log.e("PROGRESS","listOfSensorData null");
            return null;
        }

        return listOfSensorData;
    }

    private List<JsonSensorData> getSensorDataFromDatabase(String urlPath, int sensorIndex) {
        if (sensorIndex == -1) {
            return null;
        }

        List<JsonSensorData> listOfSensorData = new ArrayList<JsonSensorData>();
        URL url = null;
        try {
            url = new URL(urlPath);
        } catch (MalformedURLException e) {
            e.printStackTrace();

            return listOfSensorData;
        }
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return listOfSensorData;
        }
        if (urlConnection != null) {

            try {
                return JsonStreamReader.readJsonSensorDataStream(urlConnection.getInputStream(), sensorIndex);
            } catch (IOException e) {
                e.printStackTrace();
                return listOfSensorData;
            } finally {
                urlConnection.disconnect();
            }
        }
        return listOfSensorData;
    }

    private int getNewestIndex() {
        String urlPath = "https://duffin.co/uo/getIndex.php";

        List<JsonSensorMessage> listOfSensors = new ArrayList<JsonSensorMessage>();
        URL url = null;
        try {
            url = new URL(urlPath);
        } catch (MalformedURLException e) {
            e.printStackTrace();

            return -1;
        }
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        if (urlConnection != null) {

            try {
                return JsonStreamReader.readHighestIndex(urlConnection.getInputStream()) - 10;
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            } finally {
                urlConnection.disconnect();
            }
        }
        return -1;
    }

    private List<JsonSensorMessage> getDataFromDatabase(String urlPath) {
        List<JsonSensorMessage> listOfSensors = new ArrayList<JsonSensorMessage>();
        URL url = null;
        try {
            url = new URL(urlPath);
        } catch (MalformedURLException e) {
            e.printStackTrace();

            return listOfSensors;
        }
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return listOfSensors;
        }
        if (urlConnection != null) {
            try {
                return JsonStreamReader.readSensorJsonStream(urlConnection.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
                return listOfSensors;
            } finally {
                urlConnection.disconnect();
            }
        }
        return listOfSensors;
    }

    /**
     * back button listener, returns to home screen
     * <p>
     * Created by Petr Makarov
     */
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * adds a heatmap overlay, takes Weighted points
     *
     * @Stephen Northrop
     */
    private void addHeatMap(List<WeightedLatLng> points) {
        if (mOverlay != null) {
            mOverlay.remove();
        }
        mProvider = new HeatmapTileProvider.Builder().weightedData(points).radius(radiusBlur).gradient(new Gradient(colours, startPoints)).build();
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }

    /**
     * Just test points for debugging
     *
     * @Stephen Northrop
     */
    private List<WeightedLatLng> getTestPoints() {
        List<WeightedLatLng> list = new ArrayList<>();
        double lat = startlocation.latitude;
        double lon = startlocation.longitude;

        for (double y = 0; y < 0.005; y += 0.001)
            for (double x = 0; x < 0.005; x += 0.001) {
                list.add(new WeightedLatLng(new LatLng(lat + x, lon + y), 10));
            }
        return list;
    }

    /**
     * gets all sensor data from server
     * Stephen N
     */
    public List<JsonSensorMessage> getAllSensorData() {
        List<JsonSensorMessage> empty = new ArrayList<JsonSensorMessage>();
        String message = "";
        URL url = null;
        try {
            url = new URL("https://duffin.co/uo/getSensors.php");
        } catch (MalformedURLException e) {
            e.printStackTrace();

            return empty;
        }
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return empty;
        }
        if (urlConnection != null) {
            try {
                Log.e("PROGRESS", "Calling");
                return JsonStreamReader.readSensorJsonStream(urlConnection.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
                return empty;
            } finally {
                urlConnection.disconnect();
            }
        }
        return empty;
    }

    private HashMap<String,JsonSensorMessage> createSensorMap(){
        HashMap<String,JsonSensorMessage> map = new HashMap<>();
        List<JsonSensorMessage> sensors = getAllSensorData();
        for(JsonSensorMessage sensor: sensors){
            map.put(sensor.getID(),sensor);
        }
        return map;
    }


    // Rheyn Scholtz, place sensors on the map (viewing temp)
    List<JsonSensorMessage> sensorsToBePlaced;

    private void SensorPlacement() {
        sensorsToBePlaced = getAllSensorData();

        if (sensorsToBePlaced != null) {
            PlaceSensorsOnMap();
        } else {
            Log.e("STATE", "else");
            mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(54.973701, -1.626498))
                    .title("test"));
            Log.e("STATE", "marker created");
        }
    }

    private void PlaceSensorsOnMap() {
        for (JsonSensorMessage sensor : sensorsToBePlaced) {
            mMap.addMarker(new MarkerOptions()
                    .position(sensor.getLatLng())
                    .title(sensor.getSensorName()));
        }
    }

    public List<Pin> getAllPins(){
        List<Pin> empty = new ArrayList<Pin>();
        String message = "";
        URL url = null;
        try {
            url = new URL("https://duffin.co/uo/getPins.php");
        } catch (MalformedURLException e) {
            e.printStackTrace();

            return empty;
        }
        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
            return empty;
        }
        if (urlConnection != null) {
            try {
                return JsonStreamReader.readJsonPinStream(urlConnection.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
                return empty;
            } finally {
                urlConnection.disconnect();
            }
        }
        return empty;
    }

    public BitmapDescriptor createText(String text) {

        Paint textPaint = new Paint();
        textPaint.setTypeface(Typeface.SERIF);
        textPaint.setTextSize(30);

        float textWidth = textPaint.measureText(text);
        float textHeight = textPaint.getTextSize();

        int width = (int) textWidth;
        int height = (int) textHeight;

        Bitmap image = Bitmap.createBitmap(width, height + 10, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(image);

        canvas.translate(0, height);
        canvas.drawText(text, 0, 0, textPaint);
        BitmapDescriptor textBitmap = BitmapDescriptorFactory.fromBitmap(image);
        return textBitmap;
    }

    @Override
    public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {

    }

    @Override
    public void onChartLongPressed(MotionEvent me) {

    }

    @Override
    public void onChartScale(MotionEvent me, float scaleX, float scaleY) {

    }

    @Override
    public void onChartSingleTapped(MotionEvent me) {

    }

    @Override
    public void onNothingSelected() {

    }

    @Override
    public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {

    }

    @Override
    public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {

    }

    @Override
    public void onChartDoubleTapped(MotionEvent me) {

    }

    @Override
    public void onChartTranslate(MotionEvent me, float dX, float dY) {

    }

    public void onPMenuClick(final View view){
        PopupMenu popup = new PopupMenu(MapsActivity.this, view);
        popup.getMenuInflater().inflate(R.menu.popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {

                    case R.id.contact:
                        Intent contactUsScreen = new Intent(MapsActivity.this, ContactFormActivity.class);
                        startActivity(contactUsScreen);
                        return true;
                    case R.id.myAcc:

                        return true;
                    case R.id.logOutButton:
                        logoutDialog.show(getFragmentManager(), "logoutDialog");


                        return true;
                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    public void onRefreshClick(View view){
        return;
    }




}