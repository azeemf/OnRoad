package com.onroad.app.ui.container

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.onroad.app.R
import com.onroad.app.databinding.ActivityContainerBinding
import com.onroad.app.ui.auth.login.LoginActivity
import com.onroad.app.ui.base.BaseActivity
import com.onroad.app.ui.event.EventFragment
import com.onroad.app.ui.home.HomeFragment
import com.onroad.app.ui.poi.PoiFragment
import com.onroad.app.ui.user.UserFragment
import com.onroad.app.util.CommonKeys
import com.onroad.app.util.getPreferences
import com.onroad.app.util.goToActivity
import com.onroad.app.util.goToFragment
import org.greenrobot.eventbus.EventBus
import javax.inject.Inject

class ContainerActivity : BaseActivity(), ContainerContract.View {

    @Inject
    lateinit var presenter: ContainerContract.Presenter<ContainerContract.View>

    lateinit var binding: ActivityContainerBinding

    //fragments que va a contener la lista
    private val homeFragment = HomeFragment();
    private val poiFragment = PoiFragment();
    private val eventFragment = EventFragment();
    private val userFragment = UserFragment();

    private val listFragments = listOf(homeFragment, eventFragment, poiFragment, userFragment);

    private val navStack = arrayListOf(0);

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        binding = ActivityContainerBinding.inflate(layoutInflater);
        setContentView(binding.root);

        activityComponent.inject(this);
        presenter.attachView(this);

        supportFragmentManager.beginTransaction().replace(binding.fcvContainer.id, listFragments[0] as Fragment).commit()
        setMenuItemActive(0)
        navStack.add(0)
        initView();
    }

    override fun initView() {

        //comprobamos si se ha visto el tutorial, si no se ha visto lo mostramos
        checkHasSeenTutorial();

        //pedimos permisos de notificaciones push si hace falta
        checkNotiPermission();

        //ponemos eventos de la toolbar
        setupToolbar();

        //ponemos clicks al menu lateral
        setupClicksMenuDrawer();

        //ponemos clicks al menu inferior
        setupClicksMenuBottom();

    }

    private fun setupToolbar() {

        binding.apply {
            setSupportActionBar(iToolbar.toolbar)

            actionBarDrawerToggle = object : ActionBarDrawerToggle(
                this@ContainerActivity, drawerLayout, iToolbar.toolbar, R.string.drawer_open, R.string.drawer_close
            ) {
                override fun onDrawerClosed(view: View) {
                    supportInvalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
                }

                override fun onDrawerOpened(drawerView: View) {
                    supportInvalidateOptionsMenu() // creates call to onPrepareOptionsMenu()
                }
            }

            iToolbar.toolbar.setNavigationOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
            supportActionBar!!.setDisplayShowTitleEnabled(false)
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
            drawerLayout.addDrawerListener(actionBarDrawerToggle)
            actionBarDrawerToggle.syncState()
        }
    }

    private fun checkHasSeenTutorial() {
        //si no se ha vio el tutorial lo mostramos
        if (!getPreferences().getBoolean(CommonKeys.KEY_HAS_SEEN_TUTORIAL, false)) {
            //indicamos que el tutorial se ha visto
            //se pone aqui porque si por ejemplo peta por oom (Out of Memory) al mostrar una de las imagenes del tutorial
            //si vuelve a abrir la app no le vuelva a petar
            //https://gyazo.com/eac8c9a8d3f445487e9ff6570fafdd34
            //goToActivity(TutorialActivity::class.java);
        }
    }

    private fun checkNotiPermission() {
        //si es android 13 o superior y aun no acepto los permisos de recibir notificaciones push los pedimos
        //si es android 12 o inferior por defecto ya los va a tener activos, por lo que no hace falta hacer nada
        //(si un dispositivo con android 12 actualiza a android 13, los va a tener tambien activos:
        //https://developer.android.com/develop/ui/views/notifications/notification-permission#existing-apps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            //los pedimos
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Manejar el clic en el icono de la hamburguesa
        return if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            true
        } else {
            return when (item.itemId) {
                android.R.id.home -> {
                    if (binding.drawerLayout.isDrawerVisible(Gravity.LEFT)) {
                        binding.drawerLayout.closeDrawer(Gravity.LEFT)
                    } else {
                        binding.drawerLayout.openDrawer(Gravity.LEFT)
                    }
                    super.onOptionsItemSelected(item)
                }

                else -> super.onOptionsItemSelected(item)
            }
        }
    }

    private fun setupClicksMenuDrawer() {
        binding.apply {
            liMenuHome.setOnClickListener {
                frHome.performClick()
                binding.drawerLayout.closeDrawer(Gravity.LEFT)
            }
            liMenuDocs.setOnClickListener {
                binding.drawerLayout.closeDrawer(Gravity.LEFT)
                //goToActivity(DocsActivity::class.java)
            }
            liMenuConfig.setOnClickListener {
                binding.drawerLayout.closeDrawer(Gravity.LEFT)
                //goToActivity(SettingsActivity::class.java)
            }
            liMenuHelp.setOnClickListener {
                binding.drawerLayout.closeDrawer(Gravity.LEFT)
                //goToActivity(HelpActivity::class.java)
            }
            btnCallSos.setOnClickListener {
                val call = Intent(Intent.ACTION_DIAL)
                call.setData(Uri.parse("tel:112"))
                startActivity(call)
            }
        }
    }

    private fun setupClicksMenuBottom() {
        binding.apply {

            frHome.setOnClickListener {
                if (isNavigationNeeded(0)) {
                    setMenuItemActive(0);
                    navStack.add(0)
                    goToFragment(binding.fcvContainer.id, listFragments[0]);
                }
            }

            frPoi.setOnClickListener {
                if (isNavigationNeeded(2)) {
                    setMenuItemActive(2);
                    navStack.add(2)
                    dataManager.configProvider.selectedScreenType = 2
                    goToFragment(binding.fcvContainer.id, listFragments[2]);
                }
            }

            frEvent.setOnClickListener {
                if (isNavigationNeeded(1)) {
                    setMenuItemActive(1);
                    navStack.add(1)
                    dataManager.configProvider.selectedScreenType = 1
                    goToFragment(binding.fcvContainer.id, listFragments[1]);
                }
            }

            frUser.setOnClickListener {
                if (isNavigationNeeded(3)) {
                    if (dataManager.usuarioProvider.userLogged) {
                        setMenuItemActive(3);
                        navStack.add(3)
                        goToFragment(binding.fcvContainer.id, listFragments[3]);
                    }
                    else {
                        goToActivity(LoginActivity::class.java)
                    }
                }
            }
        }
    }

    private fun isNavigationNeeded(index: Int): Boolean = navStack.lastOrNull() != index;

    private fun setMenuItemActive(position: Int) {
        binding.apply {
            //recorremos los hijos del container del menu inferior
            repeat(llBottomMenu.childCount) { i ->
                //obtenemos el hijo
                val child = llBottomMenu.getChildAt(i);

                //comprobamos que sea relative layout (siempre deberia de serlo)
                if (child is FrameLayout) {
                    //recorremos las views hijas de la view hija que estamos iterando
                    repeat(child.childCount) { j ->
                        //obtenemos la view hija
                        val grandchild = child.getChildAt(j);

                        if (grandchild is FrameLayout) {
                            //si es la posicion seleccionada lo ponemos morado, si no en negro
                            if (i == position) {
                                grandchild.background = AppCompatResources.getDrawable(root.context, R.drawable.selected_tab)

                                repeat(grandchild.childCount) { k ->
                                    val imagechild = grandchild.getChildAt(k);

                                    if (imagechild is ImageView) {
                                        DrawableCompat.setTint(imagechild.drawable, getColor(R.color.white))
                                        grandchild.alpha = 1.0f
                                    }
                                }
                            } else {
                                grandchild.background = getDrawable(android.R.color.transparent)

                                repeat(grandchild.childCount) { k ->
                                    val imagechild = grandchild.getChildAt(k);

                                    if (imagechild is ImageView) {
                                        //si es la posicion seleccionada ponemos el icono de la lista de seleccionados, si no de la lista de no seleccionados
                                        DrawableCompat.setTint(imagechild.drawable, getColor(R.color.main_golden_light))
                                        grandchild.alpha = 0.6f
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onBackPressedCallback() {
        //si no hay ninguna peticion pendiente
        //volvemos al fragment anterior si lo hay
        //(si hay fragment anterior, popBackStackImmediate devolvera true y no entrara por el if)
        //(si no hay fragment anterior, popBackStackImmediate devolvera false y entrara por el if)
        if (!supportFragmentManager.popBackStackImmediate()) {
            finish();
        }

        //eliminamos la ultima posicion que es la que contenia el fragment desde el que se pulso el boton volver
        navStack.removeLastOrNull();

        //si hay items es que no se cerro la app
        //si lastOrNull devuelve null es que no tiene elementos y la app se cerro al no haber mas historial de navegacion
        //obtenemos el ultimo item el cual contiene la posicion del fragment al que se vuelve y lo marcamos
        navStack.lastOrNull()?.let {
            setMenuItemActive(it);
        }
    }
}