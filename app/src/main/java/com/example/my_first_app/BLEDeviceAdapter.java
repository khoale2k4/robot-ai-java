package com.example.my_first_app;

import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BLEDeviceAdapter extends RecyclerView.Adapter<BLEDeviceAdapter.DeviceViewHolder> {
    
    private List<BluetoothDevice> devices = new ArrayList<>();
    private OnDeviceClickListener listener;
    
    public interface OnDeviceClickListener {
        void onDeviceConnect(BluetoothDevice device);
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
            holder.deviceNameText.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.primary_blue));
            holder.connectButton.setBackgroundTintList(holder.itemView.getContext().getResources().getColorStateList(R.color.primary_blue));
        } else {
            holder.deviceNameText.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.black));
            holder.connectButton.setBackgroundTintList(holder.itemView.getContext().getResources().getColorStateList(android.R.color.darker_gray));
        }
        
        holder.connectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeviceConnect(device);
            }
        });
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
            deviceNameText = itemView.findViewById(R.id.deviceNameText);
            deviceAddressText = itemView.findViewById(R.id.deviceAddressText);
            connectButton = itemView.findViewById(R.id.connectButton);
        }
    }
} 