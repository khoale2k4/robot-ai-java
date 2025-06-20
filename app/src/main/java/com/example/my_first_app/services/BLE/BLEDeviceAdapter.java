package com.example.my_first_app;

import android.bluetooth.BluetoothDevice;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BLEDeviceAdapter extends RecyclerView.Adapter<BLEDeviceAdapter.DeviceViewHolder> {
    
    private List<BluetoothDevice> devices = new ArrayList<>();
    private OnDeviceClickListener listener;
    private BluetoothDevice connectingDevice = null;
    
    public interface OnDeviceClickListener {
        void onDeviceConnect(BluetoothDevice device);
        void onCancelConnection(BluetoothDevice device);
    }
    
    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.listener = listener;
    }
    
    public void addDevice(BluetoothDevice device) {
        // Avoid duplicates
        for (BluetoothDevice existingDevice : devices) {
            if (existingDevice.getAddress().equals(device.getAddress())) {
                return;
            }
        }
        devices.add(device);
        notifyItemInserted(devices.size() - 1);
    }
    
    public void clearDevices() {
        devices.clear();
        notifyDataSetChanged();
    }
    
    public void setConnectingDevice(BluetoothDevice device) {
        this.connectingDevice = device;
        notifyDataSetChanged();
    }
    
    public void clearConnectingDevice() {
        this.connectingDevice = null;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ble_device, parent, false);
        return new DeviceViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        
        // Add fade in animation for new items
        Animation fadeIn = AnimationUtils.loadAnimation(holder.itemView.getContext(), R.anim.fade_in);
        holder.itemView.startAnimation(fadeIn);
        
        if (device == null) {
            holder.deviceNameText.setText("Unknown Device");
            holder.deviceAddressText.setText("Unknown Address");
            holder.connectButton.setEnabled(false);
            return;
        }
        
        String deviceName = "Unknown Device";
        String deviceAddress = "Unknown Address";
        
        try {
            // Safely get device address
            deviceAddress = device.getAddress();
            if (deviceAddress == null || deviceAddress.isEmpty()) {
                deviceAddress = "Unknown Address";
            }
            
            // Safely get device name with permission check
            String name = device.getName();
            if (name != null && !name.isEmpty()) {
                deviceName = name;
            }
        } catch (SecurityException e) {
            Log.w("BLEDeviceAdapter", "SecurityException accessing device info", e);
        } catch (Exception e) {
            Log.w("BLEDeviceAdapter", "Exception accessing device info", e);
        }
        
        holder.deviceNameText.setText(deviceName);
        holder.deviceAddressText.setText(deviceAddress);
        
        // Highlight OhStem devices
        if (deviceName.toLowerCase().contains("ohstem")) {
            holder.deviceNameText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.mint_accent));
        } else {
            holder.deviceNameText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.primary_text));
        }
        
        // Update button state based on connection status
        boolean isConnecting = connectingDevice != null && connectingDevice.getAddress().equals(device.getAddress());
        
        if (isConnecting) {
            holder.connectButton.setText("HỦY");
            
            // Force orange color using backgroundTint - the nuclear option!
            ColorStateList orangeColorStateList = ColorStateList.valueOf(
                ContextCompat.getColor(holder.itemView.getContext(), R.color.cancel_orange)
            );
            holder.connectButton.setBackgroundTintList(orangeColorStateList);
            holder.connectButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.black));
            
            holder.connectButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCancelConnection(device);
                }
            });
        } else {
            holder.connectButton.setText("KẾT NỐI");
            
            // Reset to green button style using backgroundTint
            ColorStateList greenColorStateList = ColorStateList.valueOf(
                ContextCompat.getColor(holder.itemView.getContext(), R.color.mint_accent)
            );
            holder.connectButton.setBackgroundTintList(greenColorStateList);
            holder.connectButton.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), android.R.color.black));
            
            holder.connectButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeviceConnect(device);
                }
            });
        }
    }
    
    @Override
    public int getItemCount() {
        return devices.size();
    }
    
    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceNameText;
        TextView deviceAddressText;
        Button connectButton;
        
        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceNameText = itemView.findViewById(R.id.deviceName);
            deviceAddressText = itemView.findViewById(R.id.deviceAddress);
            connectButton = itemView.findViewById(R.id.connectButton);
        }
    }
} 