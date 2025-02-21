// This file was generated by PermissionsDispatcher. Do not modify!
package activities;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.lang.Override;
import java.lang.String;
import java.lang.ref.WeakReference;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.PermissionUtils;

final class BaseActivityPermissionsDispatcher {
  private static final int REQUEST_REQUESTALL = 0;

  private static final String[] PERMISSION_REQUESTALL = new String[] {"android.permission.READ_PHONE_STATE","android.permission.WRITE_EXTERNAL_STORAGE","android.permission.ACCESS_COARSE_LOCATION"};

  private static final int REQUEST_REQUESTSTORAGE = 1;

  private static final String[] PERMISSION_REQUESTSTORAGE = new String[] {"android.permission.WRITE_EXTERNAL_STORAGE"};

  private BaseActivityPermissionsDispatcher() {
  }

  static void RequestStorageWithPermissionCheck(@NonNull BaseActivity target) {
    if (PermissionUtils.hasSelfPermissions(target, PERMISSION_REQUESTSTORAGE)) {
      target.RequestStorage();
    } else {
      ActivityCompat.requestPermissions(target, PERMISSION_REQUESTSTORAGE, REQUEST_REQUESTSTORAGE);
    }
  }

  static void RequestAllWithPermissionCheck(@NonNull BaseActivity target) {
    if (PermissionUtils.hasSelfPermissions(target, PERMISSION_REQUESTALL)) {
      target.RequestAll();
    } else {
      if (PermissionUtils.shouldShowRequestPermissionRationale(target, PERMISSION_REQUESTALL)) {
        target.RequestAllRatinale(new BaseActivityRequestAllPermissionRequest(target));
      } else {
        ActivityCompat.requestPermissions(target, PERMISSION_REQUESTALL, REQUEST_REQUESTALL);
      }
    }
  }

  static void onRequestPermissionsResult(@NonNull BaseActivity target, int requestCode,
      int[] grantResults) {
    switch (requestCode) {
      case REQUEST_REQUESTSTORAGE:
      if (PermissionUtils.verifyPermissions(grantResults)) {
        target.RequestStorage();
      }
      break;
      case REQUEST_REQUESTALL:
      if (PermissionUtils.verifyPermissions(grantResults)) {
        target.RequestAll();
      } else {
        if (!PermissionUtils.shouldShowRequestPermissionRationale(target, PERMISSION_REQUESTALL)) {
          target.RequestAllNever();
        } else {
          target.RequestAllDenied();
        }
      }
      break;
      default:
      break;
    }
  }

  private static final class BaseActivityRequestAllPermissionRequest implements PermissionRequest {
    private final WeakReference<BaseActivity> weakTarget;

    private BaseActivityRequestAllPermissionRequest(@NonNull BaseActivity target) {
      this.weakTarget = new WeakReference<BaseActivity>(target);
    }

    @Override
    public void proceed() {
      BaseActivity target = weakTarget.get();
      if (target == null) return;
      ActivityCompat.requestPermissions(target, PERMISSION_REQUESTALL, REQUEST_REQUESTALL);
    }

    @Override
    public void cancel() {
      BaseActivity target = weakTarget.get();
      if (target == null) return;
      target.RequestAllDenied();
    }
  }
}
