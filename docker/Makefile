# makefile to build and publish images
# You can override version and repo
#  make image version=3.19.0 repo=iipc/

.PHONY: all run run-contrib image-all image image-contrib publish-all publish publish-contrib clean-all clean clean-contrib

version ?= 3.19.0
repo = 
#pwd ?= $(shell pwd)

all: image-all publish-all

image-all: image image-contrib
publish-all: publish publish-contrib
clean-all: clean clean-contrib

# ------------------------------------

image:
	docker build --build-arg version=$(version) -t heritrix:$(version) .
	docker tag heritrix:$(version) $(repo)heritrix
	docker tag heritrix:$(version) $(repo)heritrix:$(version)
	docker tag heritrix:$(version) $(repo)heritrix:$(version)-jre

image-contrib:
	docker build --build-arg version=$(version) --target contrib -t heritrix:$(version)-contrib .
	docker tag heritrix:$(version)-contrib $(repo)heritrix:contrib
	docker tag heritrix:$(version)-contrib $(repo)heritrix:$(version)-contrib
	docker tag heritrix:$(version)-contrib $(repo)heritrix:$(version)-contrib-jre

# ------------------------------------

publish: image
	docker push $(repo)heritrix
	docker push $(repo)heritrix:$(version)
	docker push $(repo)heritrix:$(version)-jre

publish-contrib: image-contrib
	docker push $(repo)heritrix:contrib
	docker push $(repo)heritrix:$(version)-contrib
	docker push $(repo)heritrix:$(version)-contrib-jre

# ------------------------------------

clean:
	docker image rm $(repo)heritrix
	docker image rm $(repo)heritrix:$(version)
	docker image rm $(repo)heritrix:$(version)-jre
	docker image rm heritrix:$(version) || true

clean-contrib:
	docker image rm $(repo)heritrix:contrib
	docker image rm $(repo)heritrix:$(version)-contrib
	docker image rm $(repo)heritrix:$(version)-contrib-jre
	docker image rm heritrix:$(version)-contrib || true

# ------------------------------------

run:
	[ -d jobs ] || mkdir jobs
	docker run --rm -it --name heritrix_container -p 8443:8443 -e "USERNAME=admin" -e "PASSWORD=admin" -v $$(pwd)/jobs:/opt/heritrix/jobs $(repo)heritrix:$(version)

run-contrib:
	[ -d jobs ] || mkdir jobs
	docker run --rm -it --name heritrix_container -p 8443:8443 -e "USERNAME=admin" -e "PASSWORD=admin" -v $$(pwd)/jobs:/opt/heritrix/jobs $(repo)heritrix:$(version)-contrib

# ------------------------------------
