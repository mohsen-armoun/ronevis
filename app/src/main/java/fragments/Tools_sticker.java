package fragments;

import android.graphics.Point;
import android.os.Bundle;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import activities.MainActivity;
import fragments.adapter.RecyclerImageAdapter;
import fragments.db.dl.FilesInfo;
import fragments.download.listener.OnItemClickListener;
import fragments.objectHelper.ImageHelper;
import fragments.objects.ImageProperties;
import fragments.tool.RegexFileFilter;
import fragments.tool.Util;
import mt.karimi.ronevis.ApplicationLoader;
import mt.karimi.ronevis.R;

public class Tools_sticker extends BaseFragment implements OnItemClickListener<File> {
    RecyclerView recyclerView;
    FilesInfo mfilesInfo;
    File PackageDir;
    private List<File> StickerFiles;
    private RecyclerImageAdapter mAdapter;

    public Tools_sticker() {
        recyclerView = null;
        StickerFiles = null;
        mAdapter = null;
    }

    public static Tools_sticker newInstance(FilesInfo filesInfo) {
        Tools_sticker fragment = new Tools_sticker();
        Bundle bundle = new Bundle();
        bundle.putSerializable("EXTRA_SUB_CAT_INFO", filesInfo);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mAdapter = null;
        StickerFiles = null;
        recyclerView = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private boolean isCurrentListViewItemVisible(int position) {
        GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        return first <= position && position <= last;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);
        recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onItemClick(View v, int position, File appInfo) {
        switch (v.getId()) {
            case R.id.imgbgsingle:
                final File currentBG = StickerFiles.get(position);
                int textID;
                textID = Util.randInt();
                ImageProperties tp = new ImageProperties()
                        .setImageViewID(textID)
                        .setImageViewSrc(currentBG.getAbsolutePath())
                        .setImageViewScaleType(ImageView.ScaleType.MATRIX)
                        .setImageViewAlpha(1.0f)
                        .setImageViewRotate(0.0f)
                        .setImageViewSize(ImageHelper.GetBitmapSize(currentBG.getAbsolutePath()))
                        .setImageViewPosition(new Point(0, 0))
                        .setImageViewGravity(Gravity.CENTER_HORIZONTAL);
                MainActivity.mainInstance().ImageViewMap.put(textID, tp);
                tp.AddImageView(MainActivity.mainInstance()._exportroot);
                break;
            case R.id.singleRowFontDelete:
                if (appInfo.exists()) {
                    boolean deleted = appInfo.delete();
                    if (deleted) {
                        try {
//                            File fileToDeleteImg = new File(ApplicationLoader.appInstance().storage.getFile(ApplicationLoader.appInstance().getString(R.string.ronevisPathBackGroundsImg)) + "/" + filesInfo.getSubcat() + "_" + i + ".jpg");
//                            if (fileToDeleteImg.exists()) {
//                                fileToDeleteImg.delete();
//                            }
                        } catch (Exception ignored) {
                            FireHelper fireHelper = new FireHelper();
                            fireHelper.SendReport(ignored);
                        }
                    }
                } else {
                    Toast.makeText(getContext(), Util.Persian(R.string.dfFileDeleted), Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    public File[] listFilesMatching(File root, String regex) {
        if (!root.isDirectory()) {
            throw new IllegalArgumentException(root + " is no directory.");
        }
        return root.listFiles(new RegexFileFilter(regex));
    }

    @Override
    public void onItemClick(View v, int position, File file, String fragmenttag) {
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        StickerFiles = new ArrayList<>();
        try {
            Bundle bundle = getArguments();
            mfilesInfo = (FilesInfo) bundle.getSerializable("EXTRA_SUB_CAT_INFO");
            PackageDir = ApplicationLoader.appInstance().storage.getFile(ApplicationLoader.appInstance().getString(R.string.ronevisPathStickers));
            mAdapter = new RecyclerImageAdapter(false);
            mAdapter.setOnItemClickListener(this);
            if (ApplicationLoader.appInstance().storage.isDirectoryExists(ApplicationLoader.appInstance().getString(R.string.ronevisPathStickers))) {
                StickerFiles.addAll(Arrays.asList(listFilesMatching(PackageDir, mfilesInfo.getSubcat() + "_*\\d*")));
            }
            recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 5));
            recyclerView.setAdapter(mAdapter);
            recyclerView.setHasFixedSize(true);
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                    rv.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                }

                @Override
                public void onTouchEvent(RecyclerView rv, MotionEvent e) {
                }

                @Override
                public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                }
            });
            mAdapter.setData(StickerFiles);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}