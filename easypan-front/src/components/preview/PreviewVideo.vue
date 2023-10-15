<template>
  <div ref="player" id="player"></div>
</template>

<script setup>
import DPlayer from "dplayer";
import { nextTick, onMounted, ref, getCurrentInstance } from "vue";
const { proxy } = getCurrentInstance();

const props = defineProps({
  url: {
    type: String,
  },
});

const videoInfo = ref({
  video: null,
});

const player = ref();
const initPlayer = () => {
  const dp = new DPlayer({
    element: player.value,
    theme: "#b7daff",
    screenshot: true,
    video: {
      //  pic: videoInfo.img, // 封面
      url: `/api${props.url}`,
      type: "customHls",
      customType: {
        customHls: function (video, player) {
          const hls = new Hls();
          hls.loadSource(video.src);
          hls.attachMedia(video);
        },
      },
    },
  });
};

onMounted(() => {
  initPlayer();
});
</script>

<style lang="scss" scoped>
#player {
  width: 100%;
  :deep .dplayer-video-wrap {
    .dplayer-video {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
  }
}
</style>
