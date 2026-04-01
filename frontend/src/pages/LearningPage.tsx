import { useNavigate } from "react-router-dom";
import AppLayout from "@/components/layout/AppLayout";
import MainHeader from "@/components/layout/MainHeader";
import AuthStatus from "@/features/auth/AuthStatus";
import { SparkIcon } from "@/components/icons/Icon";
import styles from "./LearningPage.module.css";

const learningEmptyState = {
  title: "你还没有加入星球",
  description: "加入感兴趣的圈子后，这里会显示你的专属内容流与更新提醒。",
  actionLabel: "去发现圈子"
};

const LearningPage = () => {
  const navigate = useNavigate();

  return (
    <AppLayout
      header={
        <MainHeader
          headline="我的星球"
          subtitle="跟进已加入圈子的动态、讨论和精选专栏。"
          rightSlot={<AuthStatus />}
        />
      }
    >
      <div className={styles.emptyCard}>
        <div className={styles.icon}>
          <SparkIcon width={32} height={32} stroke="none" fill="#fff" />
        </div>
        <div className={styles.title}>{learningEmptyState.title}</div>
        <div className={styles.description}>{learningEmptyState.description}</div>
        <button type="button" className="ghost-button" onClick={() => navigate("/search") }>
          {learningEmptyState.actionLabel}
        </button>
      </div>
    </AppLayout>
  );
};

export default LearningPage;
