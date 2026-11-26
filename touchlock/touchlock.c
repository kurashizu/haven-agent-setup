/* touchlock lock <seconds> | unlock | status
 * Grabs the touchscreen (EVIOCGRAB) so physical touches are ignored; injected input still works.
 * Released on: timeout, any volume key, unlock, or process death. */
#include <dirent.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define PIDF "/data/local/tmp/touchlock.pid"
#define OVER "/sdcard/Android/data/com.local.agentpill/files/override"
#define RECTS "/sdcard/Android/data/com.local.agentpill/files/rects" /* W H, take x1 y1 x2 y2, term x1 y1 x2 y2 (screen px) */
#define SVC "am start-foreground-service -n com.local.agentpill/.PillService --es state taken >/dev/null 2>&1"
#define MAXSEC 120
#define BIT_SET(a, b) ((a[(b) / 8] >> ((b) % 8)) & 1)

static void read_rects(int *r) {
    FILE *f = fopen(RECTS, "r");
    for (int i = 0; i < 10; i++)
        if (!f || fscanf(f, "%d", &r[i]) != 1) { r[0] = 0; break; }
    if (f) fclose(f);
}

static pid_t holder(void) {
    int pid = 0; char p[64], c[32] = "";
    FILE *f = fopen(PIDF, "r");
    if (!f) return 0;
    if (fscanf(f, "%d", &pid) != 1) pid = 0;
    fclose(f);
    if (pid <= 1) return 0;
    snprintf(p, sizeof p, "/proc/%d/comm", pid);
    if (!(f = fopen(p, "r"))) return 0;
    if (!fgets(c, sizeof c, f)) c[0] = 0;
    fclose(f);
    return strncmp(c, "touchlock", 9) ? 0 : pid;
}

/* the user takes over: block further phone operations until handed back */
static void takeover(int ufd_touch, int open_terminal) {
    close(open(OVER, O_WRONLY | O_CREAT, 0660));
    ioctl(ufd_touch, EVIOCGRAB, 0);
    if (system(SVC)) {}
    if (open_terminal && system("am start -n sh.haven.app/.MainActivity >/dev/null 2>&1")) {}
    _exit(0);
}

static void release(void) {
    pid_t p = holder();
    if (p) { kill(p, SIGKILL); usleep(100000); }
    unlink(PIDF);
}

int main(int argc, char **argv) {
    const char *cmd = argc > 1 ? argv[1] : "status";
    if (!strcmp(cmd, "unlock")) { release(); puts("unlocked"); return 0; }
    if (!strcmp(cmd, "status")) { puts(holder() ? "locked" : "unlocked"); return 0; }
    if (strcmp(cmd, "lock")) return 2;
    int sec = argc > 2 ? atoi(argv[2]) : 60;
    if (sec < 1 || sec > MAXSEC) sec = MAXSEC;

    if (!access(OVER, F_OK)) { puts("refused: user override"); return 3; }
    release();

    struct pollfd fds[16]; int n = 0, touch = 0, keys = 0, tfd = -1, maxx = 0, maxy = 0;
    DIR *d = opendir("/dev/input");
    struct dirent *e;
    while (d && (e = readdir(d)) && n < 16) {
        char path[300];
        unsigned char abs[ABS_MAX / 8 + 1] = {0}, key[KEY_MAX / 8 + 1] = {0}, prop[INPUT_PROP_MAX / 8 + 1] = {0};
        if (strncmp(e->d_name, "event", 5)) continue;
        snprintf(path, sizeof path, "/dev/input/%s", e->d_name);
        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd < 0) continue;
        ioctl(fd, EVIOCGBIT(EV_ABS, sizeof abs), abs);
        ioctl(fd, EVIOCGBIT(EV_KEY, sizeof key), key);
        ioctl(fd, EVIOCGPROP(sizeof prop), prop);
        if (BIT_SET(abs, ABS_MT_POSITION_X) && BIT_SET(prop, INPUT_PROP_DIRECT)) {
            if (ioctl(fd, EVIOCGRAB, 1)) { perror("grab"); return 1; }
            touch++;
            struct input_absinfo ax, ay;
            if (tfd < 0 && !ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &ax) && !ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &ay)) {
                tfd = fd; maxx = ax.maximum; maxy = ay.maximum;
            }
        } else if (BIT_SET(key, KEY_VOLUMEUP) || BIT_SET(key, KEY_VOLUMEDOWN)) {
            fds[n].fd = fd; fds[n].events = POLLIN; n++; keys++;
        } else close(fd);
    }
    /* never lock without a working escape key */
    if (!touch || !keys) { fprintf(stderr, "no touchscreen or no volume key\n"); return 1; }

    pid_t child = fork();
    if (child < 0) return 1;
    if (child) {
        FILE *f = fopen(PIDF, "w");
        if (!f) { kill(child, SIGKILL); return 1; }
        fprintf(f, "%d\n", child); fclose(f);
        printf("locked %ds (touch=%d keys=%d)\n", sec, touch, keys);
        return 0;
    }
    setsid();
    signal(SIGHUP, SIG_IGN);
    for (int i = 0; i < 3; i++) close(i);
    alarm(sec + 2);  /* backstop if the loop ever stalls */
    struct timespec start, now;
    clock_gettime(CLOCK_BOOTTIME, &start);
    int x = -1, y = -1, down = 0;
    if (tfd >= 0) { fds[n].fd = tfd; fds[n].events = POLLIN; n++; }  /* last entry: touchscreen */
    for (;;) {
        clock_gettime(CLOCK_BOOTTIME, &now);
        if (now.tv_sec - start.tv_sec >= sec) break;
        if (poll(fds, n, 500) <= 0) continue;
        for (int i = 0; i < n; i++) {
            struct input_event ev;
            if (fds[i].revents & (POLLERR | POLLHUP)) _exit(0);
            while (read(fds[i].fd, &ev, sizeof ev) == sizeof ev) {
                if (fds[i].fd != tfd) {
                    if (ev.type == EV_KEY && ev.value == 1 && (ev.code == KEY_VOLUMEUP || ev.code == KEY_VOLUMEDOWN))
                        takeover(tfd, 0);
                    continue;
                }
                if (ev.type == EV_ABS && ev.code == ABS_MT_POSITION_X) x = ev.value;
                if (ev.type == EV_ABS && ev.code == ABS_MT_POSITION_Y) y = ev.value;
                if (ev.type == EV_ABS && ev.code == ABS_MT_TRACKING_ID && ev.value >= 0) down = 1;
                if (ev.type == EV_SYN && ev.code == SYN_REPORT && down) {
                    down = 0;
                    int r[10] = {0};
                    read_rects(r);
                    if (x < 0 || y < 0 || !r[0] || !maxx || !maxy) continue;
                    int sx = (long)x * r[0] / (maxx + 1), sy = (long)y * r[1] / (maxy + 1);
                    if (sx >= r[2] && sy >= r[3] && sx <= r[4] && sy <= r[5]) takeover(tfd, 0);
                    if (sx >= r[6] && sy >= r[7] && sx <= r[8] && sy <= r[9]) takeover(tfd, 1);
                }
            }
        }
    }
    _exit(0);
}
