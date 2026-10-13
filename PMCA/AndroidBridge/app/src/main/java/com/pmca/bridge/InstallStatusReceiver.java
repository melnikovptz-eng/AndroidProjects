package com.pmca.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.widget.Toast;

public class InstallStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation;
            if (Build.VERSION.SDK_INT >= 33) {
                confirmation = intent.getParcelableExtra(
                        Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }

            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirmation);
            } else {
                Toast.makeText(
                        context,
                        "Android не передал экран подтверждения установки.",
                        Toast.LENGTH_LONG).show();
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(
                    context,
                    "PMCA успешно установлен.",
                    Toast.LENGTH_LONG).show();
        } else {
            String message = intent.getStringExtra(
                    PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(
                    context,
                    "Установка не завершена: " + status
                            + (message == null ? "" : " — " + message),
                    Toast.LENGTH_LONG).show();
        }
    }
}
