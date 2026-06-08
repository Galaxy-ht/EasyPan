<template>
  <div class="sys-setting-panel">
    <el-form
      :model="formData"
      :rules="rules"
      ref="formDataRef"
      label-width="150px"
      @submit.prevent
    >
      <!--input输入-->
      <el-form-item label="注册邮件标题" prop="registerEmailTitle">
        <el-input
          clearable
          placeholder="请输入注册邮件验证码邮件标题"
          v-model="formData.registerEmailTitle"
        ></el-input>
      </el-form-item>
      <!--textarea输入-->
      <el-form-item label="注册邮件标题" prop="registerEmailContent">
        <el-input
          clearable
          placeholder="请输入注册邮件验证码邮件内容%s占位符为验证码内容"
          v-model="formData.registerEmailContent"
        ></el-input>
      </el-form-item>
      <el-form-item label="初始空间大小" prop="userInitUseSpace">
        <el-input
          clearable
          placeholder="初始空间大小"
          v-model="formData.userInitUseSpace"
        >
          <template #suffix>MB</template>
        </el-input>
      </el-form-item>
      <!-- 单选 -->
      <el-form-item label="" prop="">
        <el-button type="primary" @click="saveSettings">保存</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance } from "vue";
import { useRouter, useRoute } from "vue-router";
const { proxy } = getCurrentInstance();
const router = useRouter();
const route = useRoute();

const api = {
  getSysSettings: "/admin/getSysSettings",
  saveSettings: "/admin/saveSysSettings",
};

const formData = ref({});
const formDataRef = ref();
const rules = {
  registerEmailTitle: [
    { required: true, message: "请输入注册邮件验证码邮件标题" },
  ],
  registerEmailContent: [
    { required: true, message: "请输入注册邮件验证码邮件内容" },
  ],
  userInitUseSpace: [
    { required: true, message: "请输入初始化空间大小" },
    {
      validator: proxy.Verify.number,
      message: "空间大小只能是数字",
    },
  ],
};

const getSysSettings = async () => {
  formData.value = {
    registerEmailTitle: '演示环境不可用',
    registerEmailContent: '演示环境不可用',
    userInitUseSpace: 5,
  };
};

const saveSettings = async () => {
  formDataRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }
    proxy.Message.success('演示环境不支持保存设置');
  });
};
</script>

<style lang="scss" scoped>
.sys-setting-panel {
  margin-top: 20px;
  width: 600px;
}
</style>