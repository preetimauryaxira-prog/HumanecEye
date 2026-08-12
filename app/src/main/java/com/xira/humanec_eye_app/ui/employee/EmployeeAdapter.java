package com.xira.humanec_eye_app.ui.employee;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.xira.humanec_eye_app.ui.camera.FileAccess;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.model.Employee;
import com.xira.humanec_eye_app.ui.camera.RegisterActivity;
import com.xira.humanec_eye_app.utils.ToastUtils;
import java.util.ArrayList;
import java.util.List;

public class EmployeeAdapter extends RecyclerView.Adapter<EmployeeAdapter.EmployeeViewHolder> {

    private List<Employee> employees = new ArrayList<>();
    private final Context context;
    private boolean isRegisteredTab;
    private final ApiRepository apiRepository;

    private final EmployeeFragment employeeFragment;

    public EmployeeAdapter(Context context, boolean isRegisteredTab,EmployeeFragment employeeFragment) {
        this.context = context;
        this.isRegisteredTab = isRegisteredTab;
        apiRepository = ApiRepository.getInstance(context);
        this.employeeFragment=employeeFragment;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setIsRegisteredTab(boolean isRegisteredTab) {
        this.isRegisteredTab = isRegisteredTab;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_employee, parent, false);
        return new EmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmployeeViewHolder holder, int position) {
        Employee employee = employees.get(position);
        holder.nameTextView.setText(employee.getEmpName());
        holder.empCode.setText(employee.getCode());

        // Get Image URL
        String imageUrl = (employee.getImgAttn() != null && !employee.getImgAttn().isEmpty()) ? employee.getImgAttn() :
                (employee.getImg() != null && !employee.getImg().isEmpty()) ? employee.getImg() : null;

        // Load profile image
        if (imageUrl != null) {
            Picasso.get()
                    .load(imageUrl)
                    .placeholder(R.drawable.user)
                    .error(R.drawable.user)
                    .fit()
                    .centerCrop()
                    .transform(new RotateTransformation(employee.getImgAttn()))
                    .into(holder.imageView);
        } else {
            holder.imageView.setImageResource(R.drawable.user);
        }

        // Handle button click for registered/unregistered employees
        if (isRegisteredTab) {
            holder.btnMoreOptions.setBackgroundResource(R.drawable.delete);
            holder.btnMoreOptions.setOnClickListener(v -> {
                holder.btnMoreOptions.setEnabled(false);// Disable button
                holder.btnMoreOptions.setBackgroundResource(R.drawable.loading); // Show loading icon

                showDeregisterDialog(employee, holder);
            });
        } else {
            holder.btnMoreOptions.setBackgroundResource(R.drawable.camera);
            holder.btnMoreOptions.setOnClickListener(v -> {
                holder.btnMoreOptions.setEnabled(false); // Disable button
                holder.btnMoreOptions.setBackgroundResource(R.drawable.loading); // Show loading icon

                registerEmployee(employee, holder);
            });
        }
    }

    private void showDeregisterDialog(Employee employee, EmployeeViewHolder holder) {
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.RoundedAlertDialog)
                .setTitle("De-register")
                .setMessage("Are you sure you want to de-register this employee? This action cannot be undone.")
                .setPositiveButton("Yes", (dialogInterface, which) -> {
                    deleteEmployee(employee.getCode(), holder);
                })
                .setNegativeButton("No", (dialogInterface, which) -> {
                    holder.btnMoreOptions.setEnabled(true); // Re-enable button if cancelled
                    holder.btnMoreOptions.setImageResource(R.drawable.delete); // Reset button image
                    dialogInterface.dismiss();
                })
                .create();

        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(context, R.color.red_button));
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.textColor));


    }


    private void deleteEmployee(String empCode, EmployeeViewHolder holder) {
        apiRepository.deleteEmployee(empCode, context).observe((LifecycleOwner) context, success -> {
            if (success) {
                removeFace(empCode);
                removeEmployeeFromList(empCode);
                employeeFragment.refreshEmployeeList();
                ToastUtils.showSuccessToast(context, "Employee de-registered successfully");
            } else {
                ToastUtils.showErrorToast(context, "Failed to de-register employee");
            }

            holder.btnMoreOptions.setEnabled(true); // Re-enable button
            holder.btnMoreOptions.setImageResource(R.drawable.delete); // Reset button image
        });
    }
    private void removeEmployeeFromList(String empCode) {
        for (int i = 0; i < employees.size(); i++) {
            if (employees.get(i).getCode().equals(empCode)) {
                employees.remove(i);
                notifyItemRemoved(i);
                break; // Exit the loop once the employee is found and removed
            }
        }
    }

    private void removeFace(String employeeCode) {
        try {
            var fileAccess=new FileAccess(context);

            fileAccess.removeFaceByCode(employeeCode);
        } catch (Exception e) {
            Log.e("Employee Register", "Error removing face data", e);
            ToastUtils.showErrorToast(context, "Failed to remove face");
        }
    }
    private void registerEmployee(Employee employee, EmployeeViewHolder holder) {
        Intent intent = new Intent(context, RegisterActivity.class);
        intent.putExtra("empCode", employee.getCode());
        intent.putExtra("empName", employee.getEmpName());
        intent.putExtra("registration_mode", true);
        context.startActivity(intent);
        holder.btnMoreOptions.setEnabled(true); // Re-enable button
        holder.btnMoreOptions.setImageResource(R.drawable.camera); // Reset button image
        employeeFragment.refreshEmployeeList();
    }


    @Override
    public int getItemCount() {
        return employees.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setEmployeeList(List<Employee> employees) {
        this.employees = employees;
        notifyDataSetChanged();
    }

    static class EmployeeViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, empCode;
        ImageView imageView;
        ImageView btnMoreOptions;

        EmployeeViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.ivEmployeeImage);
            nameTextView = itemView.findViewById(R.id.tvEmployeeName);
            empCode = itemView.findViewById(R.id.tvEmpCode);
            btnMoreOptions = itemView.findViewById(R.id.btnMoreOptions);
        }
    }
}

