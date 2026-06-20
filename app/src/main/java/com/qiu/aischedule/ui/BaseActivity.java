package com.qiu.aischedule.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.qiu.aischedule.R;

/**
 * 所有 Activity 的基类：根治"装饰层 ActionBar 与布局内容重叠"。
 *
 * 根因：targetSdk 36 强制 edge-to-edge，内容框架变成整屏（从窗口顶 y=0 铺满），
 * 而装饰层（DarkActionBar）ActionBar 是叠在内容之上的另一层——内容会画到 ActionBar 后面，
 * 半透明 ActionBar 时标题与内容重叠。在"装饰层 ActionBar + 内容"两层架构上猜偏移治标不治本。
 *
 * 彻底解法（本基类配合 NoActionBar 主题 + 各布局内 MaterialToolbar）：
 *  ① 拥抱 edge-to-edge（setDecorFitsSystemWindows=false）；
 *  ② 把布局里的 MaterialToolbar 设为 ActionBar（标题/菜单/返回由此承载）；
 *  ③ 把系统栏 inset 作为内容根的 padding——Toolbar（首子视图）落在状态栏下方、
 *     内容落在 Toolbar 下方。标题与内容同在内容层、纵向堆叠，重叠在结构上不可能发生。
 *
 * 子类无需改动：onCreate 首行 super.onCreate 触发 ①，setContentView 经本类重写触发 ②③。
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 拥抱 edge-to-edge（SDK35+ 默认），手动吃系统栏 inset
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        setupChrome();
    }

    /** MaterialToolbar 设为 ActionBar；系统栏 inset 作为内容根 padding。 */
    private void setupChrome() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), bars.bottom);
            return insets;
        });
    }
}
