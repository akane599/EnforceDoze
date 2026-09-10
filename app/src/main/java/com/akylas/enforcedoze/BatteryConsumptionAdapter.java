package com.akylas.enforcedoze;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class BatteryConsumptionAdapter extends BaseAdapter {
    private ArrayList<BatteryConsumptionItem> listData;
    private LayoutInflater layoutInflater;

    public BatteryConsumptionAdapter(Context aContext, ArrayList<BatteryConsumptionItem> listData) {
        this.listData = listData;
        layoutInflater = LayoutInflater.from(aContext);
    }

    @Override
    public int getCount() {
        return listData.size();
    }

    @Override
    public Object getItem(int position) {
        return listData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.list_row_layout_stats, parent, false);
            holder = new ViewHolder();
            holder.timestamp = (TextView) convertView.findViewById(R.id.dozeStateTimestamp);
            holder.batteryPerc = (TextView) convertView.findViewById(R.id.batteryLevel);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        String record = listData.get(position).getTimestampPercCombo();
        String[] data = record == null ? new String[0] : record.split(",");
        holder.timestamp.setText(""); holder.batteryPerc.setText("");
        try {
            if (data.length != 3) throw new IllegalArgumentException("Malformed record");
            long timestamp = Long.parseLong(data[0]);
            float battery = Float.parseFloat(data[1]);
            if (!Float.isFinite(battery)) throw new IllegalArgumentException("Invalid battery level");
            holder.timestamp.setText(data[2] + " · " + Utils.getDateCurrentTimeZone(timestamp));
            holder.batteryPerc.setText(battery < 0 ? parent.getContext().getString(R.string.stats_charging) : battery + "%");
        } catch (RuntimeException e) { holder.timestamp.setText(record == null ? "" : record); }
        return convertView;
    }

    static class ViewHolder {
        TextView timestamp;
        TextView batteryPerc;
    }
}