package com.example.criminalintent.controller.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.telecom.Call;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.example.criminalintent.R;
import com.example.criminalintent.databinding.FragmentCrimeDetailBinding;
import com.example.criminalintent.model.Crime;
import com.example.criminalintent.repository.CrimeDBRepository;
import com.example.criminalintent.repository.IRepository;
import com.example.criminalintent.utils.PictureUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class CrimeDetailFragment extends Fragment {

    public static final String TAG = "CDF";
    public static final String BUNDLE_CRIME = "crime";
    public static final String ARG_CRIME_ID = "CrimeId";
    public static final String DIALOG_FRAGMENT_TAG = "Dialog";

    private FragmentCrimeDetailBinding mCrimeDetailBinding;


    public static final int REQUEST_CODE_DATE_PICKER = 0;
    public static final int REQUEST_CODE_SELECT_CONTACT = 1;
    private static final int REQUEST_CODE_IMAGE_CAPTURE = 2;
    public static final String FILEPROVIDER_AUTHORITY = "com.example.criminalintent.fileprovider";

    private Crime mCrime;
    private IRepository<Crime> mRepository;
    private File mPhotoFile;

/*    private EditText mEditTextCrimeTitle;
    private Button mButtonDate;
    private CheckBox mCheckBoxSolved;
    private Button mButtonSuspect;
    private Button mButtonShareReport;
    private ImageButton mImageButtonCaptureImage;
    private ImageView mImageViewPicture;*/

    private Callbacks mCallbacks;

    public CrimeDetailFragment() {
        //empty public constructor
    }

    /**
     * Using factory pattern to create this fragment. every class that want
     * to create this fragment should always call this method "only".
     * no class should call constructor any more.
     *
     * @param crimeId this fragment need crime id to work properly.
     * @return new CrimeDetailFragment
     */
    public static CrimeDetailFragment newInstance(UUID crimeId) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_CRIME_ID, crimeId);

        CrimeDetailFragment fragment = new CrimeDetailFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof Callbacks) {
            mCallbacks = (Callbacks) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement onCrimeUpdated");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mRepository = CrimeDBRepository.getInstance(getActivity());

        UUID crimeId = (UUID) getArguments().getSerializable(ARG_CRIME_ID);
        mCrime = mRepository.get(crimeId);

        mPhotoFile = mRepository.getPhotoFile(getActivity(), mCrime);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        mCrimeDetailBinding = DataBindingUtil.inflate(
                inflater,
                R.layout.fragment_crime_detail,
                container,
                false);

        initViews();

        return mCrimeDetailBinding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(BUNDLE_CRIME, mCrime);
    }

    /*@Override
    public void onPause() {
        super.onPause();

        updateCrime();
    }*/


    private void initViews() {
        mCrimeDetailBinding.setCrime(mCrime);
        if (mCrime.getSuspect() != null)
            mCrimeDetailBinding.chooseSuspect.setText(mCrime.getSuspect());

        updatePhotoView();
    }

    public class Clicks {
        public void ChangeTitle() {
            //   mCrime.setTitle(charSequence.toString());
            Log.d(TAG, mCrime.toString());
        }

        public void changeSolved(boolean checked) {
            mCrime.setSolved(checked);
            Log.d(TAG, mCrime.toString());

            updateCrime();
        }

        public void selectDate() {
            DatePickerFragment datePickerFragment = DatePickerFragment.newInstance(mCrime.getDate());

            //create parent-child relations between CrimeDetailFragment-DatePickerFragment
            datePickerFragment.setTargetFragment(CrimeDetailFragment.this, REQUEST_CODE_DATE_PICKER);

            datePickerFragment.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        }

        public void shareCrime() {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, getReportText());
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crime_report_subject));
            sendIntent.setType("text/plain");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            if (sendIntent.resolveActivity(getActivity().getPackageManager()) != null)
                startActivity(shareIntent);
        }

        public void SelectSuspect() {
            Intent pickContactIntent = new Intent(Intent.ACTION_PICK);
            pickContactIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            if (pickContactIntent.resolveActivity(getActivity().getPackageManager()) != null)
                startActivityForResult(pickContactIntent, REQUEST_CODE_SELECT_CONTACT);
        }

        public void captureImage() {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                if (mPhotoFile == null)
                    return;

                Uri photoURI = FileProvider.getUriForFile(
                        getActivity(),
                        FILEPROVIDER_AUTHORITY,
                        mPhotoFile);

                grantTemPermissionForTakePicture(takePictureIntent, photoURI);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_CODE_IMAGE_CAPTURE);
            }
        }
    }


    private void grantTemPermissionForTakePicture(Intent takePictureIntent, Uri photoURI) {
        PackageManager packageManager = getActivity().getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(
                takePictureIntent,
                PackageManager.MATCH_DEFAULT_ONLY);

        for (ResolveInfo activity : activities) {
            getActivity().grantUriPermission(activity.activityInfo.packageName,
                    photoURI,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    private String getReportText() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        String dateString = simpleDateFormat.format(mCrime.getDate());

        String solvedString = mCrime.isSolved() ?
                getString(R.string.crime_report_solved) :
                getString(R.string.crime_report_unsolved);

        String suspectString = mCrime.getSuspect() == null ?
                getString(R.string.crime_report_no_suspect) :
                getString(R.string.crime_report_suspect, mCrime.getSuspect());

        String report = getString(R.string.crime_report,
                mCrime.getTitle(),
                dateString,
                solvedString,
                suspectString);

        return report;
    }

    private void updateCrime() {
        mRepository.update(mCrime);
        mCallbacks.onCrimeUpdated(mCrime);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null)
            return;

        if (requestCode == REQUEST_CODE_DATE_PICKER) {
            //get response from intent extra, which is user selected date
            Date userSelectedDate = (Date) data.getSerializableExtra(DatePickerFragment.EXTRA_USER_SELECTED_DATE);

            mCrime.setDate(userSelectedDate);
            mCrimeDetailBinding.crimeDate.setText(mCrime.getDate().toString());

            updateCrime();
        } else if (requestCode == REQUEST_CODE_SELECT_CONTACT) {
            Uri contactUri = data.getData();

            String[] columns = new String[]{ContactsContract.Contacts.DISPLAY_NAME};
            Cursor cursor = getActivity().getContentResolver().query(contactUri,
                    columns,
                    null,
                    null,
                    null);

            if (cursor == null || cursor.getCount() == 0)
                return;

            try {
                cursor.moveToFirst();

                String suspect = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                mCrime.setSuspect(suspect);
                updateCrime();

                mCrimeDetailBinding.chooseSuspect.setText(mCrime.getSuspect());
            } finally {
                cursor.close();
            }
        } else if (requestCode == REQUEST_CODE_IMAGE_CAPTURE) {
            updatePhotoView();
            Uri photoUri = FileProvider.getUriForFile(
                    getActivity(),
                    FILEPROVIDER_AUTHORITY,
                    mPhotoFile);
            getActivity().revokeUriPermission(photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    private void updatePhotoView() {
        if (mPhotoFile == null || !mPhotoFile.exists()) {
            mCrimeDetailBinding.crimePicture.setImageDrawable(getResources().getDrawable(R.drawable.ic_person));
        } else {
            Bitmap bitmap = PictureUtils.getScaledBitmap(mPhotoFile.getPath(), getActivity());
            mCrimeDetailBinding.crimePicture.setImageBitmap(bitmap);
        }
    }

    public interface Callbacks {
        void onCrimeUpdated(Crime crime);
    }
}