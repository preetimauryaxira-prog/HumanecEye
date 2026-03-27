package com.xira.humanec_eye_app.ui.employee;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.tabs.TabLayout;
import com.xira.humanec_eye_app.databinding.FragmentRegisterBinding;
import com.xira.humanec_eye_app.model.Employee;

import java.util.List;

public class EmployeeFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private EmployeeAdapter adapter;
    private EmployeeViewModel employeeViewModel;
    private EditText searchRegisterField;
    private SwipeRefreshLayout swipeRefreshLayout;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private boolean isRegisteredTab = true;
    private SharedPreferences sharedPreferences;
    private TextView companyName;
    private ShimmerFrameLayout shimmerLayout;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        employeeViewModel = new ViewModelProvider(this).get(EmployeeViewModel.class);
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize views
        RecyclerView employeesList = binding.employeeList;
        searchRegisterField = binding.searchEmployeeField;
        swipeRefreshLayout = binding.swipeRefresh;
        TabLayout tabLayout = binding.tabLayout;
        companyName = binding.tvCompanyName;
        shimmerLayout = binding.shimmerLayout;

        sharedPreferences = getContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        companyName.setText(sharedPreferences.getString("orgName", ""));
        employeesList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new EmployeeAdapter(getContext(), isRegisteredTab, this);
        employeesList.setAdapter(adapter);

        // Initialize data - fetch both lists
        employeeViewModel.initData(requireContext());

        // Show Shimmer effect when data is loading
        shimmerLayout.startShimmer();
        shimmerLayout.setVisibility(View.VISIBLE);
        employeesList.setVisibility(View.GONE);

        companyName.setOnClickListener(view -> showBusinessOption());

        // Setup tab selection listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Clear focus and hide keyboard when tab changes
                if (searchRegisterField.hasFocus()) {
                    searchRegisterField.clearFocus();
                    hideKeyboard();
                }

                // Clear search field (optional)
                searchRegisterField.setText("");

                isRegisteredTab = tab.getPosition() == 0;
                adapter.setIsRegisteredTab(isRegisteredTab);
                fetchEmployees(""); // Search with empty query

                // Update search hint based on tab
                searchRegisterField.setHint(isRegisteredTab ?
                        "Search registered employee" : "Search unregistered employee");
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Observe registered employees
        employeeViewModel.getRegisteredEmployees().observe(getViewLifecycleOwner(), employees -> {
            if (isRegisteredTab && employees != null) {
                updateUI(employees);
            }
        });

        // Observe unregistered employees
        employeeViewModel.getUnregisteredEmployees().observe(getViewLifecycleOwner(), employees -> {
            if (!isRegisteredTab && employees != null) {
                updateUI(employees);
            }
        });

        // Observe counts to update tabs
        employeeViewModel.getRegisteredCount().observe(getViewLifecycleOwner(), count -> {
            updateTabCounts();
        });

        employeeViewModel.getUnregisteredCount().observe(getViewLifecycleOwner(), count -> {
            updateTabCounts();
        });

        // Observe loading states
        employeeViewModel.getIsLoadingRegistered().observe(getViewLifecycleOwner(), isLoading -> {
            if (isRegisteredTab) {
                handleLoadingState(isLoading);
            }
        });

        employeeViewModel.getIsLoadingUnregistered().observe(getViewLifecycleOwner(), isLoading -> {
            if (!isRegisteredTab) {
                handleLoadingState(isLoading);
            }
        });

        // Implement search with 300ms debounce
        searchRegisterField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchRunnable = () -> fetchEmployees(s.toString().trim());
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });
        // Swipe to refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            fetchEmployees("");
        });

        return root;
    }
    private void hideKeyboard() {
        if (getActivity() != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getView() != null) {
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
            }
        }
    }
    private void updateUI(List<Employee> employees) {
        swipeRefreshLayout.setRefreshing(false);
        adapter.setEmployeeList(employees);
        // Hide Shimmer and show RecyclerView
        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);
        binding.employeeList.setVisibility(View.VISIBLE);
    }

    private void handleLoadingState(boolean isLoading) {
        if (isLoading) {
            shimmerLayout.startShimmer();
            shimmerLayout.setVisibility(View.VISIBLE);
            binding.employeeList.setVisibility(View.GONE);
        } else {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            binding.employeeList.setVisibility(View.VISIBLE);
        }
    }

    private void updateTabCounts() {
        TabLayout.Tab registeredTab = binding.tabLayout.getTabAt(0);
        TabLayout.Tab unregisteredTab = binding.tabLayout.getTabAt(1);

        Integer registeredCount = employeeViewModel.getRegisteredCount().getValue();
        Integer unregisteredCount = employeeViewModel.getUnregisteredCount().getValue();

        if (registeredCount != null) {
            registeredTab.setText("Registered(" + registeredCount + ")");
        }
        if (unregisteredCount != null) {
            unregisteredTab.setText("Unregistered(" + unregisteredCount + ")");
        }
    }

    private void fetchEmployees(String query) {
        if (isRegisteredTab) {
            employeeViewModel.fetchRegisteredEmployees(query, requireContext());
        } else {
            employeeViewModel.fetchUnregisteredEmployees(query, requireContext());
        }
    }

    private void showBusinessOption() {
        BottomSheetOption bottomSheet = new BottomSheetOption();
        bottomSheet.setEmployeeFragment(this);
        bottomSheet.show(getParentFragmentManager(), "Select Business");
    }

    public void updateCompanyName(String newCompanyName) {
        companyName.setText(newCompanyName);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("orgName", newCompanyName);
        editor.apply();
    }

    public void refreshEmployeeList() {
        fetchEmployees("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        binding = null;
    }
}