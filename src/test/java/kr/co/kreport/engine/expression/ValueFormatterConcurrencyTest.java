package kr.co.kreport.engine.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 금액을 여러 스레드가 동시에 서식화해도 같은 글자가 나오는지.
 *
 * <p>리포트는 웹 요청마다 다른 스레드에서 돌고, 모든 금액 칸이 같은 서식 패턴을 쓴다.
 * 서식기를 스레드 사이에 나눠 쓰면 숫자가 섞여 나올 수 있는데, 예외가 나지 않고 조용히
 * 틀린 금액이 찍히므로 결재가 끝난 뒤에야 드러난다.</p>
 */
class ValueFormatterConcurrencyTest {

    @Test
    @DisplayName("여러 스레드가 서로 다른 금액을 동시에 찍어도 각자 제 값이 나와야 한다")
    void amountsDoNotBleedAcrossThreads() throws Exception {
        String pattern = "#,##0.00";
        int threads = 32;
        int perThread = 20000;

        // 틀린 결과만 모은다. 비어 있어야 정상.
        Set<String> wrong = new ConcurrentSkipListSet<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            // 스레드마다 다른 금액. 실제 리포트도 행마다 값이 다르다.
            // 자릿수를 1자리부터 20자리까지 크게 벌린다. 내부 버퍼가 늘었다 줄면 충돌이 드러나기 쉽다.
            BigDecimal amount = new BigDecimal("9".repeat(t % 20 + 1) + "." + (t % 90 + 10));
            String expected = new java.text.DecimalFormat(pattern).format(amount);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        String actual = ValueFormatter.format(amount, pattern);
                        if (!expected.equals(actual)) {
                            wrong.add(amount + " -> " + actual + " (기대 " + expected + ")");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    wrong.add(amount + " -> 예외 " + e.getClass().getSimpleName());
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(wrong)
                .as("동시에 찍은 금액이 서로 섞였습니다")
                .isEmpty();
    }
}
