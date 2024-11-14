package com.dane.printerdemo;

import android.app.DatePickerDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import recieptservice.com.recieptservice.PrinterInterface;
import okhttp3.*;

public class MainActivity extends AppCompatActivity {
    MyReceiver myReceiver = null;
    private PrinterInterface printerService = null;
    private boolean isBound = false;
    private TextView dateTextView;
    private TextView employTextView;
    private TextView machineType;
    private EditText amountEdit;
    private int role;
    private int user_id;
    private int manager_id;
    private String manager_name;
    private String SN;
    private EditText machineId;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Egor code begins from here
        // UI components member variables
        role = -1;
        SN = Settings.Secure.getString(MainActivity.this.getContentResolver(), Settings.Secure.ANDROID_ID);
        machineId = MainActivity.this.findViewById(R.id.txt_machineID);
        machineType = MainActivity.this.findViewById(R.id.lbl_company);
        employTextView = MainActivity.this.findViewById(R.id.txt_employee);
        dateTextView = findViewById(R.id.date_current);
        String currentDate = new SimpleDateFormat("MM-dd-yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime());
        dateTextView.setText(currentDate);
        Button datePickerButton = findViewById(R.id.btn_datePicker);
        datePickerButton.setOnClickListener(v -> showDatePickerDialog());
        MainActivity.this.findViewById(R.id.btn_clear).setOnClickListener(v -> machineId.setText(""));
        MainActivity.this.findViewById(R.id.btn_print).setOnClickListener(v -> {
            amountEdit = (EditText) MainActivity.this.findViewById(R.id.num_amount);
            String inputText = amountEdit.getText().toString().trim();
            int k = 0; // default value or handle this case differently if needed
            if(role == -1) showConfirmDialog(true);
            else{
                if (!inputText.isEmpty()) {
                    try {
                        k = Integer.parseInt(inputText);
                    } catch (NumberFormatException e) {
                        Toast.makeText(MainActivity.this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
                        return; // Stop further execution
                    }
                } else {
                    // Handle empty input case, e.g., show a message to the user
                    Toast.makeText(MainActivity.this, "Input is required", Toast.LENGTH_SHORT).show();
                    return; // Stop further execution
                }
                if (isBound) {
                    if(k > 499){
                        showConfirmDialog(false);
                    }
                    else {
                        printTicket();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Printer is not ready", Toast.LENGTH_SHORT).show();
                }
            }
        });
        // bind Printer service
        Intent intent = new Intent();
        intent.setClassName("recieptservice.com.recieptservice", "recieptservice.com.recieptservice.service.PrinterService");
        bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE);
        // Start the barcode scanner
        myReceiver = new MyReceiver();
        myReceiver.parent = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(myReceiver, new IntentFilter("android.scanner.scan"), Context.RECEIVER_EXPORTED);
            Toast.makeText(MainActivity.this, "Scanner ready", Toast.LENGTH_SHORT).show();
        }
        showConfirmDialog(true);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver);
    }
    // Define the ServiceConnection to handle the binding
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Get the AIDL interface from the IBinder
            printerService = PrinterInterface.Stub.asInterface(service);
            isBound = true;
            Toast.makeText(MainActivity.this, "Service connected", Toast.LENGTH_LONG).show();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
            isBound = false;
            Toast.makeText(MainActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
        }
    };
    private void showConfirmDialog(boolean login) {
        // Create a layout to hold the EditTexts
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Create EditText for Username
        final EditText usernameInput = new EditText(this);
        usernameInput.setHint("Username");
        layout.addView(usernameInput); // Add username field to layout

        // Create EditText for Password
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passwordInput); // Add password field to layout

        // Build the AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(login?"Login":"Manager Confirmation");
        builder.setView(layout); // Set the layout as the dialog view

        // Add dialog buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String username = usernameInput.getText().toString();
                String password = passwordInput.getText().toString();
                // Handle login logic here
                try {
                    ApiService apiService = new ApiService();
                    String json = "{\"username\":\""+username+"\",\"password\":\""+password+"\"}";
                    apiService.fetchData("http://quiqct.com/api/login", json,new ApiService.Callback() {
                        @Override
                        public void onSuccess(String response) {
                            try {
                                // Parse the response to JSON
                                JSONObject jsonResponse = new JSONObject(response);
                                int _role = jsonResponse.getInt("role");
                                int _user_id = jsonResponse.getInt("user_id");
                                // Extract the role field
                                if(login) {
                                    employTextView.setText(username);
                                    role = _role;
                                    user_id = _user_id;
                                    if(role < 2) {
                                        manager_id = user_id;
                                        manager_name = username;
                                    }
                                    Toast.makeText(MainActivity.this, "Welcome! " + username, Toast.LENGTH_LONG).show();
                                }
                                else
                                {
                                    if(_role == 0 || _role == 1)
                                    {
                                        manager_id = _user_id;
                                        manager_name = username;
                                        printTicket();
                                    }
                                }
                            } catch (JSONException e) {
                                Toast.makeText(MainActivity.this, "parse error login", Toast.LENGTH_LONG).show();
                            }
                        }
                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(MainActivity.this, "onError login", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "API fetch error login", Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
    private void showDatePickerDialog() {
        // Get current date
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        // Create a new DatePickerDialog instance
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                MainActivity.this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int selectedYear, int selectedMonth, int selectedDay) {
                        // Month is 0-based, so add 1 to get the correct month
                        String selectedDate = selectedYear + "-" + (selectedMonth + 1) + "-" + selectedDay;
                        // Set the selected date in the EditText
                        dateTextView.setText(selectedDate);
                    }
                },
                year, month, day);

        datePickerDialog.show();
    }
    private void printTicket(){
        try {
            // Call AIDL methods from the service
            Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.logo);
            int newWidth = 320;
            int newHeight = 240;
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true);
            String inputText = amountEdit.getText().toString().trim();
            int amount = Integer.parseInt(inputText);
            printerService.setAlignment(1);
            printerService.printText("    ");
            printerService.printBitmap(resizedBitmap);
            printerService.setTextSize(36);
            printerService.printText("\n" + machineType.getText() + "\n");
            printerService.setTextSize(24);
            printerService.printText("---------------------------\n");
            printerService.printText("Machine ID : " + machineId.getText());
            printerService.printText("\nEmployee : " + employTextView.getText());
            if(amount > 499)
                printerService.printText("\nManager : " + manager_name);
            printerService.setTextSize(32);
            printerService.printText("\nAmount : ");
            printerService.setTextBold(true);
            printerService.printText("$" + amountEdit.getText() + "\n");
            printerService.setTextSize(24);
            printerService.setTextBold(false);
            String datetime = new SimpleDateFormat("MM-dd-yyyy hh:mm:ss", Locale.getDefault()).format(Calendar.getInstance().getTime());
            printerService.printText("Date : " + datetime);
            printerService.printText("\nDevice SN : " + SN + "\n");
            String confirmationCode = generate6DigitCodeFromDate(dateTextView.getText().toString());
            printerService.setTextSize(36);
            printerService.printText("Validation : ");
            printerService.printText(confirmationCode);
            printerService.setTextSize(24);
            printerService.printText("\n---------------------------\n");
            printerService.printText("Only valid on this day.\n");
            printerService.printText("---------------------------\n\n\n\n");

            ApiService apiService = new ApiService();
            String json =   "{\"machine_id\":\""+machineId.getText()+"\"," +
                            "\"pda_id\":\"" + SN + "\"," +
                            "\"employee_id\":\"" + user_id + "\"," +
                            "\"manager_id\":\"" + manager_id + "\"," +
                            "\"amount\":\"" + amount + "\"," +
                            "\"description\":\""+ confirmationCode +"\"," +
                            "\"timestamp\":\"" + datetime + "\"}";
            apiService.fetchData("http://quiqct.com/api/print", json,new ApiService.Callback() {
                @Override
                public void onSuccess(String response) {

                    try {
                        // Parse the response to JSON
                        JSONObject jsonResponse = new JSONObject(response);
                        String success = jsonResponse.getString("success");
                        if(success.equals("true")){
                            Toast.makeText(MainActivity.this, "Success", Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(MainActivity.this, "parse error print", Toast.LENGTH_LONG).show();
                    }
                }
                @Override
                public void onError(Exception e) {
                    Toast.makeText(MainActivity.this, "onError print", Toast.LENGTH_LONG).show();
                }
            });
        } catch (RemoteException e) {
            Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_LONG).show();
        }
        machineId.setText("");
        machineType.setText(R.string.logo_text);
        amountEdit.setText("");
    }
    public void setMachineId(String text){
        machineId.setText(text);
        ApiService apiService = new ApiService();
        String json = "{\"machine_id\":\"" + text + "\"}";
        apiService.fetchData("http://quiqct.com/api/machine", json,new ApiService.Callback() {
            @Override
            public void onSuccess(String response) {
                try {
                    // Parse the response to JSON
                    JSONObject jsonResponse = new JSONObject(response);
                    String success = jsonResponse.getString("success");
                    if(success.equals("true")){
                        String make = jsonResponse.getString("make");
                        String name = jsonResponse.getString("name");
                        machineType.setText(make + "\n" + name);
                        Toast.makeText(MainActivity.this, "Success", Toast.LENGTH_LONG).show();
                    }
                    else
                    {
                        machineType.setText("Unknown");
                    }
                } catch (JSONException e) {
                    Toast.makeText(MainActivity.this, "parse error machine", Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this, "onError machine", Toast.LENGTH_LONG).show();
            }

        });
    }
    public static String generate6DigitCodeFromDate(String code) {
        try {
            Random random = new Random(code.hashCode()); // Hash the combined string to get a consistent seed
            return String.valueOf(random.nextInt(900000) + 100000);
        } catch (Exception e) {
            return "Invalid"; // Return -1 if an error occurs
        }
    }
}