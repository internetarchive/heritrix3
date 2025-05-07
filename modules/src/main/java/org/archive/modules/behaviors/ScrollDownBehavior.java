package org.archive.modules.behaviors;

/**
 * Scrolls the page down until it reaches the bottom (or until a timeout is reached).
 */
public class ScrollDownBehavior implements Behavior {
    private long timeout = 5000;
    private int scrollInterval = 50;

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setScrollInterval(int scrollInterval) {
        this.scrollInterval = scrollInterval;
    }

    @Override
    public void run(Page page) {
        page.evalPromise(/* language=JavaScript */ """
                const [timeout, scrollInterval] = arguments;
                return new Promise((doneCallback, reject) => {
                    const startTime = Date.now();
                
                    function scroll() {
                        if (window.innerHeight + window.scrollY >= document.body.offsetHeight) {
                            // We've reached the bottom of the page
                            doneCallback();
                            return;
                        }
                
                        if (Date.now() - startTime > timeout) {
                            // We've exceeded the timeout
                            doneCallback();
                            return;
                        }
                
                        const scrollStep = document.scrollingElement.clientHeight * 0.2;
                
                        window.scrollBy({top: scrollStep, behavior: "instant"});
                        setTimeout(scroll, scrollInterval);
                    }
                
                    scroll();
                })""", timeout, scrollInterval);
    }
}
